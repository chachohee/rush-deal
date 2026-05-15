# RushDeal 로컬 실행 가이드

## 시스템 요구사항

전체 스택(Spring Boot 서비스 10개 + 인프라 16개 컨테이너)은 가동 시 약 **6 GiB RAM**을 점유합니다.

| 항목 | 최소 | 권장 |
|---|---|---|
| RAM | 16 GB | 32 GB |
| Docker Desktop 메모리 할당 | 10 GB | 12 GB 이상 |
| 여유 디스크 | 20 GB | 30 GB 이상 |
| CPU | Apple M1 이상 / Intel i5 4코어 이상 | — |

> **M1/M2 MacBook Air 8 GB에서는 전체 스택 실행이 불가합니다.**
> macOS 자체가 3–4 GB를 점유하므로 Docker에 할당할 수 있는 메모리가 4–5 GB에 불과해 Spring Boot 서비스 대부분이 OOM으로 종료됩니다.
> 8 GB 환경에서는 [통합 테스트만 실행](#통합-테스트만-실행하는-경우8-gb-환경)하는 방법을 사용하세요.

### 통합 테스트만 실행하는 경우(8 GB 환경)

`docker-compose-app.yml` 전체를 띄우지 않아도 통합 테스트는 독립적으로 실행됩니다. Testcontainers가 테스트별로 격리된 컨테이너를 자동으로 띄우고 종료합니다. Java 21과 Docker Desktop(메모리 4 GB 이상 할당)만 있으면 됩니다.

```bash
./gradlew clean test --max-workers=1 --continue
```

수동 API 테스트(curl 흐름)가 필요하다면, 부하가 큰 모니터링 스택(Prometheus·Grafana·Zipkin·Kafka UI)을 제외하고 핵심 서비스만 선택적으로 올리는 방법도 있지만, 현재 `docker-compose-app.yml`에 별도 lite 프로파일은 없습니다.

## 사전 준비

- Docker Desktop (실행 중이어야 함, 메모리 10 GB 이상 할당 권장)
- Java 21 (Amazon Corretto 21)
- `.env` 파일 (루트 경로에 위치, `.env.example` 참고)
  - JWT 시크릿, PortOne 결제, MinIO 자격증명 등 채워야 함

## 실행

```bash
./start-local.sh
```

Gradle 빌드 → Docker 이미지 빌드 → 전체 서비스 실행까지 자동으로 처리됩니다.

> **주의:** 처음 실행 시 Eureka 캐시가 갱신되기까지 약 30초 소요됩니다. 그 전에 API 호출 시 503이 반환될 수 있습니다.

## 테스트 데이터 생성

서비스가 모두 뜬 후 아래 스크립트로 계정·상품·타임딜·대기열 정책·포인트를 한 번에 생성합니다.

```bash
./scripts/seed-test-data.sh
```

생성되는 항목: 계정 3종(master/seller/user) · 상품 5개(이미지 포함) · 타임딜 5개(예정 2·진행중 2·마감 1) · 재고 100개 · QueuePolicy 4개 · user 포인트 100,000P · 기본 배송지 · ES 재색인

전체 테스트 시나리오는 [docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md) 를 참고하세요.

## API 엔드포인트

단일 진입점: `http://localhost:8080`

| 기능 | 메서드 | 경로 |
|------|--------|------|
| 회원가입 | POST | `/api/v1/auth/signup` |
| 로그인 | POST | `/api/v1/auth/login` |
| 상품 조회 | GET | `/api/v1/products/**` |
| 상품 이미지 업로드 | POST | `/api/v1/products/images` (multipart) |
| 타임딜 | GET / POST | `/api/v1/timedeals/**` |
| 타임딜 검색 (ES) | GET | `/api/v1/timedeals/search` |
| 관심 상품 | POST / GET / DELETE | `/api/v1/timedeals/{id}/interest` |
| 주문 | POST | `/api/v1/orders/**` |
| 셀러 주문 요약 | GET | `/api/v1/orders/seller/me/summary` |
| 셀러 저재고 | GET | `/api/v1/stocks/seller/me/low` |
| 결제 | POST | `/api/v1/payments/**` |
| 대기열 | POST / GET | `/api/v1/queues/**` |
| 알림 | GET | `/api/v1/notifications/**` |
| 알림 WebSocket | STOMP | `ws://localhost:8080/ws` |

### 회원가입 예시

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@rushdeal.com",
    "password": "pass1234!",
    "name": "테스트유저",
    "role": "USER"
  }'
```

`role` 값: `USER` / `SELLER` / `MASTER`

### 로그인 예시

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@rushdeal.com",
    "password": "pass1234!"
  }'
```

## 관리 도구

| 도구 | 주소 | 계정 |
|------|------|------|
| Eureka (서비스 등록 현황) | http://localhost:8761 | - |
| Kafka UI | http://localhost:18080 | - |
| Grafana (모니터링) | http://localhost:3000 | admin / rushcrew2025 |
| Zipkin (분산 추적) | http://localhost:9411 | - |
| Elasticsearch (검색 인덱스) | http://localhost:9200 | - |
| MinIO Console (이미지 스토리지) | http://localhost:9001 | rushdeal / rushdeal123 |
| API Gateway 헬스체크 | http://localhost:8080/actuator/health | - |

## 서비스 포트

| 서비스 | 포트 |
|--------|------|
| API Gateway | 8080 |
| Auth Service | 8000 |
| Payment Service | 8010 |
| Product Service | 8020 |
| Timedeal Service | 8030 |
| Queue Service | 8040 |
| Order Service | 8050 |
| User Service | 8060 |
| Notification Service | 8070 |
| Discovery (Eureka) | 8761 |
| MinIO API | 9000 |
| MinIO Console | 9001 |
| Elasticsearch | 9200 |

## 인프라 구성

- **PostgreSQL** (스키마 분리): auth_schema, user_schema, product_schema, time_deal_schema, order_schema, payment_schema, notification_schema
  - 마이그레이션은 Flyway 자동 적용 (각 서비스의 `db/migration` 디렉터리)
- **Redis**: 서비스별 인스턴스 (auth/user/queue/order/timedeal/gateway)
- **Kafka + Zookeeper**: 이벤트 fanout (Saga + Outbox 패턴)
- **Elasticsearch 8.18.8 + analysis-nori**: 타임딜 한글 검색
- **MinIO**: S3 호환 오브젝트 스토리지 (상품 이미지)
  - 초기화 시 `rushdeal-products` 버킷 자동 생성 + public download 권한
  - 이미지 업로드: `POST /api/v1/products/images` (multipart/form-data), 반환된 `imageUrl` 을 상품 등록 시 사용
- **Prometheus + Grafana**: 메트릭 수집 및 대시보드 3종 (서비스/Kafka/비즈니스)
- **Zipkin**: 분산 추적

## Dockerfile 구분

| 파일 | 용도 |
|------|------|
| `Dockerfile.local` | 로컬 실행용. 로컬에서 빌드한 JAR을 그대로 복사 |
| `Dockerfile.template` | CI/CD용. Docker 내부에서 Gradle 빌드부터 처리 |
| `Dockerfile.elasticsearch` | nori 플러그인이 포함된 ES 8.18.8 커스텀 이미지 |

`start-local.sh`와 `docker-compose-app.yml`은 `Dockerfile.local`을 사용합니다.

## 프론트엔드 연동

별도 리포지토리 [`rush-deal-web`](https://github.com/chachohee/rush-deal-web) 에서 실행. 루트에 `.env.local` 파일이 필요합니다.

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

```bash
pnpm install
pnpm dev
```

## 테스트 후 초기화

### 자동화 통합 테스트(`./gradlew clean test`) 이후

별도 정리가 **필요 없습니다**. 테스트는 Testcontainers로 완전히 격리되어 실행되며, 테스트 종료 시 컨테이너가 자동 삭제됩니다. 실제 운영 컨테이너는 전혀 영향받지 않습니다.

### 수동 API 테스트(curl 흐름) 이후

**Kafka는 정리 불필요** — 컨슈머 그룹이 오프셋을 커밋했으므로 재시작해도 처리된 메시지를 다시 읽지 않습니다.

**Redis는 인스턴스별로 초기화합니다.**

```bash
# 대기열 토큰 + 스케줄러 타임스탬프
docker exec rushdeal_queue_redis redis-cli FLUSHDB

# 타임딜 재고 카운터(tds:*) + 대기열 이벤트 스트림
docker exec rushdeal_timedeal_redis redis-cli FLUSHDB
docker compose -f docker-compose-app.yml restart timedeal-service  # 재고 캐시 재로드
```

> `auth_redis`(JWT 블랙리스트)·`order_redis`·`user_redis`·`gateway_redis`는 TTL 자동 만료 또는 상시 빈 상태이므로 일반적으로 건드리지 않아도 됩니다.

**전체 리셋(DB 포함)이 필요한 경우:**

```bash
docker-compose -f docker-compose-app.yml down -v && ./start-local.sh && ./scripts/seed-test-data.sh
```

## 종료

```bash
./stop-local.sh
```

> 볼륨(데이터)까지 초기화하려면: `docker-compose -f docker-compose-app.yml down -v`
