# chapchap-customer-service

## 로컬 실행 설정

JDK 21을 사용한다. 저장소 루트를 작업 디렉터리로 설정하고, `.env.example`을 `.env`로 복사해 본인 환경에 맞게 수정한다. 기존 `.env`가 있으면 덮어쓰지 않고 누락 항목만 추가한다.

`application.yaml`의 `spring.config.import`가 `.env`를 선택적으로 읽는다. 파일이 없으면 OS 환경변수와 YAML 기본값을 사용하며, OS 환경변수는 `.env`보다 우선한다. IntelliJ에서도 Working directory를 이 저장소 루트로 지정하면 동일하게 적용된다.

```powershell
# .env를 준비한 뒤 저장소 루트에서 실행
./gradlew.bat bootRun
```

기본 주소는 `http://localhost:8084`다. APP_PORT를 설정했다면 해당 포트를 사용한다.

## 필요한 의존성

| 항목 | 설정/준비 |
|---|---|
| MySQL | DB_HOST/PORT/NAME/USER/PASSWORD. `docker-compose.yaml` 기준 localhost:3307/customer_db, 사용자 chapchap |
| DB 테이블 | JPA는 `ddl-auto=validate`로 기존 스키마를 검사한다. 빈 DB를 만들기만 해서는 시작되지 않는다. 프로젝트 DB 스키마를 별도 준비해야 한다. |
| MinIO | MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY. 지식·품질 첨부파일 업로드에는 두 private bucket 준비 필요 |
| Kafka | 이벤트 수신 시 broker와 프로젝트 topic 준비. `KAFKA_LISTENER_AUTO_STARTUP=false`면 수신을 시작하지 않는다. |

이 저장소의 Docker Compose는 MySQL만 제공한다. Kafka·MinIO와 애플리케이션을 함께 시작하거나 DB 테이블을 자동 생성하지 않는다. Compose의 MYSQL_PASSWORD와 Spring의 DB_PASSWORD는 같은 값이어야 한다. 기존 MySQL 볼륨의 계정 비밀번호는 `.env` 수정만으로 바뀌지 않는다.

예시는 Postman REST 확인을 위해 Kafka 수신과 지식 자동 활성화 scheduler를 끈다. 실제 이벤트/예약 활성화를 검증할 때 해당 플래그를 켠다. 파일은 하나당 최대 10MB이며 전체 multipart 요청은 기본 11MB다. 여러 첨부파일도 이 전체 한도에 포함되며 필요 시 MULTIPART_MAX_REQUEST_SIZE를 명시한다.

## .env 문법과 비밀값

- 이 파일은 Java properties 호환 형식이다. `KEY=value`를 사용하고 `export`, 값 주변 따옴표, 값 뒤 주석을 넣지 않는다. 주석은 별도 줄에 작성한다.
- `$`는 shell처럼 실행/치환되지 않는다. 다만 Spring의 `${...}` 표현식과 역슬래시 escape 규칙에 주의한다. 복잡한 비밀값은 OS 환경변수나 Secret 주입을 사용한다.
- 파일 내 역슬래시 자체는 `\\`로 표현한다. PEM은 한 줄의 `\n`을 실제 줄바꿈으로 읽도록 표현할 수 있다. OS 환경변수로 주입할 때는 실제 PEM 줄바꿈도 지원한다.
- .env.example의 change-me 값은 실행 계정이 아니다. 실제 비밀번호·키를 입력한 `.env`와 `.env.*`는 Git에서 제외하고 `.env.example`만 공유한다.

## AI 연동은 별도 설정

기본은 `CUSTOMER_AI_ACTIVATION_MODE=DISABLED`이며 다섯 Runtime 플래그는 모두 false다. 일반 Customer REST API를 확인하는 데 AI 인증 키는 필요하지 않다. 이 상태에서는 AI 답변·요약 및 처리 callback 완료를 기대하면 안 된다.

격리 연동은 `CUSTOMER_AI_ACTIVATION_MODE=ISOLATED`와 `SPRING_PROFILES_ACTIVE=ai-isolated`를 함께 지정한다. 상담 답변에는 internal-auth와 consultation-response 플래그, Auth client 계정과 RSA Subject 키, 실제 HTTPS AI origin이 필요하다. 지식 비동기 처리·요약에는 내부 토큰 발급과 callback 인증·각 async 플래그 및 JWKS 설정도 필요하다. `ai-isolated`는 gate용 profile 이름이며 키/주소를 자동 제공하지 않는다.

기본 `CUSTOMER_AI_BASE_URL=http://localhost:8085`는 비활성 시의 값이다. 현재 클라이언트는 AI 기능 활성화 시 HTTPS origin만 허용하므로 실제 인증서가 신뢰되는 HTTPS 주소로 바꿔야 한다. AI에서 지식 파일을 읽을 때도 MinIO presigned URL이 AI가 허용한 HTTPS 호스트여야 한다. localhost는 각 실행 환경 자신을 가리킨다.

운영 `ACTIVE`는 별도 증거 manifest가 완료되어야 한다. 설정 파일만 바꾸어 운영 gate를 우회하지 않는다. 현재 개인 현재 상태 답변은 별도 비활성 상태이며 배송은 담당 서비스 구현/계약 확정 후 연결한다. Prometheus는 사용하지 않는다.

## Postman 시작점과 검증

서버/DB 준비 후 `GET /api/customer/faqs`를 먼저 확인한다. 로컬 서비스 직접 호출의 상담 생성은 `POST /api/customer/consultations`, 헤더 `X-User-Id: <테스트 사용자 ID>`, `X-User-Role: CUSTOMER`, JSON `{"content":"환불 정책이 궁금합니다."}`다. Gateway 경유 시에는 로그인 토큰 인증 흐름을 사용한다.

```powershell
./gradlew.bat test --no-daemon --max-workers=2
```

자동 테스트는 실제 외부 MySQL/MinIO/Kafka/Auth/AI 연결 성공을 보장하지 않는다. `src/test/resources/application.yaml`은 DB 자동설정과 Kafka 등을 분리한 테스트 전용 설정이며 실행 설정 대신 사용하지 않는다.
