# Customer Docker 실행

기존 .env와 MySQL 볼륨은 보존합니다. 새 환경만 .env.example을 .env로 복사하고 비밀값을 교체합니다. 기존 docker-compose.yaml의 MySQL 서비스·container_name·볼륨 키는 유지했습니다. Compose 프로젝트명을 바꾸면 다른 볼륨이 선택될 수 있으므로 기존 실행 위치·프로젝트명을 유지하세요.

## DB 준비

```powershell
# 저장소 루트에서: 기존과 같이 MySQL만 실행
docker compose up -d mysql
```

기본 호스트 포트는 3307입니다. MYSQL_PASSWORD는 DB 앱 계정 비밀번호이고 MYSQL_ROOT_PASSWORD는 root 비밀번호입니다. 기존 볼륨의 비밀번호는 .env 변경만으로 바뀌지 않습니다. 로컬 Java 실행의 DB_PASSWORD도 MYSQL_PASSWORD와 맞춥니다.

**빈 DB만 생성하면 앱은 실행되지 않습니다.** ddl-auto=validate를 유지하므로 승인된 Customer 스키마를 별도 절차로 적용해야 합니다. 이 작업은 DB 초기화·마이그레이션을 자동 실행하지 않습니다. MySQL healthcheck는 연결 준비 확인일 뿐 스키마 준비 확인이 아닙니다.

## 앱 실행

공유 chapchap-network가 없으면 최초 한 번 생성합니다.

```powershell
docker network create chapchap-network
docker compose --profile app config --quiet
docker compose --profile app up -d --build
```

- app profile을 지정해야 Customer 앱도 실행됩니다. 일반 up은 기존 DB-only 동작입니다.
- 앱 컨테이너는 mysql:3306/customer_db, chapchap 계정과 MYSQL_PASSWORD를 사용합니다. 기존 외부 DB_HOST/DB_PORT를 컨테이너에 그대로 전달하지 않습니다.
- 앱 기본 호스트 포트는 127.0.0.1:8084, 컨테이너 포트는 8084입니다. Gateway는 customer-service:8084로 연결합니다.
- COMPOSE_KAFKA_BOOTSTRAP_SERVERS는 컨테이너에서 접근할 broker 주소입니다. advertised.listeners도 확인합니다.
- COMPOSE_MINIO_ENDPOINT는 컨테이너와 presigned URL을 사용하는 클라이언트·AI 모두 접근 가능한 주소여야 합니다. host.docker.internal은 Docker 호스트 접근 예시이며 운영 공개 주소가 아닙니다.
- private MinIO 버킷(customer-knowledge/customer-quality)·계정은 별도 준비합니다.
- AI 기본 DISABLED와 ddl validate를 유지했습니다. internal auth/callback은 HTTPS·키·계정·격리 profile을 갖춘 후 별도 활성화합니다.
- 로그와 파일 업로드 검증은 실제 Kafka/MinIO 구성 후 수행합니다.

이미지 빌드는 Java 21로 bootJar를 만들고 비root 사용자로 실행합니다. 실제 .env는 이미지에 들어가지 않습니다. 설정값은 env_file로 주입합니다. 별도 환경 파일을 사용하면 ENV_FILE과 --env-file을 함께 같은 경로로 설정합니다.

중지는 docker compose --profile app stop, 컨테이너 제거는 docker compose --profile app down입니다. **down -v는 DB 볼륨을 삭제하므로 사용하지 마세요.**
