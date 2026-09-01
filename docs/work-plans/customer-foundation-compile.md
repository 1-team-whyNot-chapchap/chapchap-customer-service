# 작업 경계: Customer Foundation Compile

> 작업 브랜치: `feature/customer-foundation-compile`
> 기준 브랜치: `dev` (`52e7569`)
> 상태: 검증 차단

## 1. 목표

Customer-Service에 남아 있는 Auth-Service 복사 잔재를 제거하고, Customer 단독 컴파일이 가능한 공통 기반을 만든다.

## 2. 이 브랜치의 책임

- `com.chapchap.auth.*` 참조 제거 및 Customer 패키지 의존성으로 정리
- Customer 책임이 아닌 OAuth, Refresh Token, Cookie, 관리자 비밀번호 변경, 본인인증 DI 코드 제거
- 공통 응답·예외·OpenAPI를 Customer-Service 기준으로 교정
- ERD 계약에 없는 generic Soft Delete 기반 제거
- 컴파일과 변경 범위 검증

## 3. 명시적 제외 범위

- Gateway Trusted User Context 구현 및 Header 인증 활성화
- JWT 검증, OAuth, Refresh Token, Cookie 인증
- DB Entity, 마이그레이션, JPA 스키마 변경
- FAQ·상담·품질 문의·Kafka·MinIO·Customer-AI 기능
- `application.yaml`, `.env`, `docker-compose.yaml` 변경
- `docs/agent-context/` SSOT 수정

## 4. 근거와 경계

| 항목 | 기준 |
|---|---|
| 서비스 소유권 | `01_CUSTOMER_REQUIREMENTS.md` 1.2, `03_CUSTOMER_POLICY.md` CS-POL-COM-001~003 |
| 코드 구조·응답 | `09_CODE_CONVENTION.md` 4~6 |
| API 문서 | `PROJECT_AGENT_RULES.md` 5.4 |
| Soft Delete | `03_CUSTOMER_POLICY.md` CS-POL-FAQ-002, `04_CUSTOMER_DB_ERD.md` FAQ 규칙 |
| 작업 절차 | `GENERAL_HARNESS/04.GATEGUARD.md`, `skills/verification-loop` |

Customer-Service는 Gateway가 검증한 사용자 문맥을 최종 권한 판단에 사용해야 하지만, Gateway의 구체 헤더·직접 접근 차단 계약은 아직 확정되지 않았다. 따라서 이 브랜치에서는 인증 Header를 신뢰하거나 활성화하지 않고, Swagger/OpenAPI 문서 경로 외 요청을 기본 차단한다.

## 5. 예정 변경

| 구분 | 대상 | 이유 |
|---|---|---|
| 수정 | `global/error`, `global/response`, `global/config/openapi` | Auth 패키지 참조 제거 및 Customer 응답 기반 정리 |
| 수정 | `global/security/filter/SecurityConfiguration` | Auth OAuth 의존 제거, 미확정 Header 인증 비활성화 |
| 삭제 | Auth 전용 security, cookie, minio, generic soft-delete 코드 | Customer 계약 밖 또는 ERD 미근거 코드 제거 |
| 생성 | 이 작업 경계 문서 | 브랜치 책임·제외 범위·검증 근거 기록 |

## 6. 위험과 중단 조건

| 위험 | 대응 |
|---|---|
| 파일 삭제 | 사용자 요청에 포함된 A 묶음 범위만 삭제하고, 삭제 목록과 diff를 검토한다. |
| 보안 | 인증 기능을 새로 구현하지 않는다. Gateway 계약이 없는 Header 인증 활성화는 다음 브랜치의 Stop Condition이다. |
| 사용자 변경 덮어쓰기 | 기존 미추적 문서·`.env`·`docker-compose.yaml`은 수정하거나 커밋하지 않는다. |

## 7. 완료 기준

- [x] `src/main/java`에 `com.chapchap.auth` 참조가 없다.
- [x] Customer 계약 밖 Auth/OAuth/Cookie/DI/profile-image/Soft Delete 코드가 없다.
- [x] OpenAPI 제목이 Customer-Service를 가리킨다.
- [ ] `./gradlew compileJava`가 통과한다. Java 21 실행 환경이 없어 검증이 차단됐다.
- [x] 변경 diff에 제외 범위 파일이 포함되지 않는다.

## 8. 검증 차단

2026-09-01에 `./gradlew.bat compileJava`를 실행했으나 `JAVA_HOME`과 `java` 명령이 모두 없는 환경이라 Gradle을 시작하지 못했다. Java 설치 또는 `JAVA_HOME` 설정은 환경 변경이므로 사용자 승인 후 처리한다.

## 9. 다음 연결

`feature/customer-security-context`: Gateway 헤더 계약이 확정된 뒤 Trusted User Context, 역할 검증, Security Gate를 구현한다.
