"""실제 Java 발급 자료를 Customer-AI HTTPS JWKS 검증기로 교차 검증한다."""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import threading
from datetime import UTC, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from uuid import UUID

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--material-dir", required=True, type=Path)
    parser.add_argument("--customer-ai-root", required=True, type=Path)
    return parser.parse_args()


def _certificate(directory: Path) -> tuple[Path, Path]:
    """localhost 전용 임시 TLS 인증서와 개인 키를 생성한다."""
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])
    now = datetime.now(UTC)
    certificate = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=1))
        .not_valid_after(now + timedelta(minutes=30))
        .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost")]), critical=False)
        .sign(key, hashes.SHA256())
    )
    certificate_path = directory / "localhost-cert.pem"
    private_key_path = directory / "localhost-key.pem"
    certificate_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
    private_key_path.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    return certificate_path, private_key_path


class _JwksHandler(BaseHTTPRequestHandler):
    documents: dict[str, bytes] = {}

    def do_GET(self) -> None:  # noqa: N802
        body = self.documents.get(self.path)
        if body is None:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format: str, *_args: object) -> None:
        """JWT나 임시 경로가 출력되지 않도록 HTTP 접근 로그를 비활성화한다."""


def _properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def _load_document(material_dir: Path, name: str) -> bytes:
    value = json.loads((material_dir / name).read_text(encoding="utf-8"))
    return json.dumps(value, separators=(",", ":")).encode()


def main() -> None:
    args = _arguments()
    material_dir = args.material_dir.resolve(strict=True)
    customer_ai_root = args.customer_ai_root.resolve(strict=True)
    sys.path.insert(0, str(customer_ai_root / "src"))

    certificate_path, private_key_path = _certificate(material_dir)
    os.environ["SSL_CERT_FILE"] = str(certificate_path)

    from chapchap_customer_ai.contracts.models import UserRole
    from chapchap_customer_ai.core.settings import Settings
    from chapchap_customer_ai.security.jwt_verifier import create_internal_security_verifier
    from chapchap_customer_ai.security.models import InternalAuthError

    service_path = "/.well-known/jwks.json"
    subject_path = "/.well-known/customer-ai-subject-jwks.json"
    _JwksHandler.documents = {
        service_path: _load_document(material_dir, "service-jwks-old.json"),
        subject_path: _load_document(material_dir, "subject-jwks-old.json"),
    }
    server = ThreadingHTTPServer(("localhost", 0), _JwksHandler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certificate_path, private_key_path)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    try:
        origin = f"https://localhost:{server.server_port}"
        settings = Settings(
            _env_file=None,
            service_jwks_url=origin + service_path,
            subject_assertion_jwks_url=origin + subject_path,
            jwks_cache_lifespan_seconds=60,
        )
        values = _properties(material_dir / "subject-context.properties")
        request_id = UUID(values["requestId"])
        consultation_id = int(values["consultationId"])
        old_service = (material_dir / "service-token-old.txt").read_text(encoding="utf-8")
        new_service = (material_dir / "service-token-new.txt").read_text(encoding="utf-8")
        old_subject = (material_dir / "subject-assertion-old.txt").read_text(encoding="utf-8")
        new_subject = (material_dir / "subject-assertion-new.txt").read_text(encoding="utf-8")

        verifier = create_internal_security_verifier(settings)
        authenticated = verifier.verify(
            "Bearer " + old_service,
            old_subject,
            expected_request_id=request_id,
            expected_consultation_id=consultation_id,
            required_subject_scope=values["scope"],
            allowed_roles={UserRole.CUSTOMER},
        )
        assert authenticated.service_subject == "customer-service"
        assert authenticated.subject.user_id == int(values["userId"])

        try:
            verifier.verify(
                "Bearer " + old_service,
                old_subject,
                expected_request_id=request_id,
                expected_consultation_id=consultation_id + 1,
                required_subject_scope=values["scope"],
                allowed_roles={UserRole.CUSTOMER},
            )
        except InternalAuthError:
            pass
        else:
            raise AssertionError("상담 문맥이 다른 Subject Assertion이 거부되지 않았습니다.")

        _JwksHandler.documents = {
            service_path: _load_document(material_dir, "service-jwks-overlap.json"),
            subject_path: _load_document(material_dir, "subject-jwks-overlap.json"),
        }
        rotation_verifier = create_internal_security_verifier(settings)
        for service_token, subject_assertion in (
            (old_service, old_subject),
            (new_service, new_subject),
        ):
            rotation_verifier.verify(
                "Bearer " + service_token,
                subject_assertion,
                expected_request_id=request_id,
                expected_consultation_id=consultation_id,
                required_subject_scope=values["scope"],
                allowed_roles={UserRole.CUSTOMER},
            )

        _JwksHandler.documents = {
            service_path: _load_document(material_dir, "service-jwks-new.json"),
            subject_path: _load_document(material_dir, "subject-jwks-new.json"),
        }
        new_only_verifier = create_internal_security_verifier(settings)
        new_only_verifier.verify(
            "Bearer " + new_service,
            new_subject,
            expected_request_id=request_id,
            expected_consultation_id=consultation_id,
            required_subject_scope=values["scope"],
            allowed_roles={UserRole.CUSTOMER},
        )
        try:
            new_only_verifier.verify_service("Bearer " + old_service)
        except InternalAuthError:
            pass
        else:
            raise AssertionError("제거된 이전 JWKS 키로 발급한 토큰이 거부되지 않았습니다.")

        unavailable_settings = Settings(
            _env_file=None,
            service_jwks_url=origin + "/missing-service-jwks",
            subject_assertion_jwks_url=origin + subject_path,
        )
        try:
            create_internal_security_verifier(unavailable_settings).verify_service(
                "Bearer " + new_service
            )
        except InternalAuthError:
            pass
        else:
            raise AssertionError("JWKS 장애가 Fail-Closed로 처리되지 않았습니다.")

        print("PASS: 실제 Java 발급기와 Customer-AI HTTPS JWKS 양방향 검증 완료")
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=5)


if __name__ == "__main__":
    main()
