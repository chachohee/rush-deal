# ⏰ Rush Deal

> 트래픽 집중 상황을 고려한 MSA 기반 타임딜 이커머스 플랫폼

![rushdeal.png](rushdeal.png)

## 목차

- [프로젝트 소개](#-프로젝트-소개)
- [프로젝트 목표](#-프로젝트-목표)
- [로컬 실행 방법](#-로컬-실행-방법)
- [주요 기능](#-주요-기능)
- [팀원 및 역할](#-팀원-및-역할)
- [기술 스택](#-기술-스택)
- [DDD 구조](#-ddd-구조-bounded-context--aggregate)
- [시스템 아키텍처](#-시스템-아키텍처)
- [주문 생성 플로우차트](#-주문-생성-플로우차트)
- [ERD](#-erd)
- [성능 검증](#-성능-검증)
- [테스트 자동화](#-테스트-자동화)
- [모니터링 대시보드](#-모니터링-대시보드)
- [프로젝트 구조](#-프로젝트-구조)

---

## 📌 프로젝트 소개

한정된 시간과 수량 안에서 주문이 집중되는 타임딜 커머스 환경의 MSA(Microservices Architecture) 기반 이커머스 플랫폼입니다.

트래픽 집중 상황에서 발생할 수 있는 **동시성·정합성·서비스 병목 문제**를 해결하기 위해 동시성 제어, 비동기 이벤트 기반 서비스 연동, Redis·Kafka·Elasticsearch·모니터링 도구를 활용한 트래픽 처리 구조를 설계하고 구현했습니다.

---

## 🚩 프로젝트 목표

### 대규모 트래픽 대응
- MSA 기반 서비스 분리로 독립적 확장성 확보
- Redis Sorted Set 대기열과 Kafka 비동기 이벤트로 트래픽 집중 구간 안정화
- 동시성 제어(Optimistic Lock, Redisson 분산 락)로 데이터 정합성 보장

### 검색·알림 등 사용자 경험 강화
- Elasticsearch(nori 형태소 분석)로 한국어 타임딜 검색 + 자동완성
- WebSocket/STOMP 기반 실시간 알림 + Kafka fanout으로 도메인 이벤트 → 사용자 알림 변환
- 관심 등록 → 타임딜 시작 시 자동 알림으로 비회원 회원가입 유도 funnel 구축

### 배포 및 운영
- Docker 기반 서비스별 실행 환경 통일
- `docker-compose-app.yml` 단일 파일로 전체 인프라·앱·모니터링 일괄 실행
- Flyway로 명시적 DB 마이그레이션 관리 (silent schema drift 방지)
- GitHub Actions + AWS ECS 기반 CI/CD 파이프라인

### 모니터링 시스템 구축
- Prometheus + Grafana 대시보드 3종 (서비스 헬스/Kafka/비즈니스 지표)
- Zipkin을 통한 분산 트레이싱 및 병목 지점 분석

---

## ▶️ 로컬 실행 방법

자세한 내용은 [LOCAL_SETUP.md](LOCAL_SETUP.md)를 참고하세요.

**사전 준비:** Docker Desktop, Java 21 (Amazon Corretto 21), `.env` 파일

```bash
./start-local.sh
```

단일 진입점: `http://localhost:8080`

| 기능 | 메서드 | 경로 |
|------|--------|------|
| 회원가입 | POST | `/api/v1/auth/signup` |
| 로그인 | POST | `/api/v1/auth/login` |
| 상품 조회 | GET | `/api/v1/products/**` |
| 상품 이미지 업로드 | POST | `/api/v1/products/images` (multipart) |
| 타임딜 목록·상세 | GET | `/api/v1/timedeals/**` |
| 타임딜 검색 | GET | `/api/v1/timedeals/search?q=...` |
| 타임딜 검색 자동완성 | GET | `/api/v1/timedeals/search/suggest?q=...` |
| 관심 등록/해제/조회 | POST/DELETE/GET | `/api/v1/timedeals/{id}/interest` |
| 내 관심 타임딜 | GET | `/api/v1/timedeals/me/interested` |
| 주문 | POST | `/api/v1/orders/**` |
| 셀러 주문 요약 | GET | `/api/v1/orders/seller/me/summary` |
| 결제 준비·완료·취소 | POST | `/api/v1/payments/**` |
| 대기열 진입·순위 조회 | POST/GET | `/api/v1/queues/**` |
| 대기열 정책 관리 | POST/PATCH | `/api/v1/queue/policies` |
| 알림 목록·읽음 | GET/PATCH | `/api/v1/notifications/**` |
| 알림 WebSocket(STOMP) | WS | `/api/v1/notifications/ws` |
| 관리자 감사 로그 | GET | `/api/v1/users/audit-logs` |
| 배송지 관리 | POST/GET | `/api/v1/users/me/addresses/**` |
| 포인트 잔액 | GET | `/api/v1/points/balance` |
| 셀러 저재고 목록 | GET | `/api/v1/stocks/seller/me/low` |

---

## 🔑 주요 기능

### Saga·Outbox 패턴 기반 고신뢰 주문 시스템
- 주문-재고-포인트 서비스 간 분산 트랜잭션을 **Orchestration Saga**로 관리, 장애 발생 시 자동 보상 트랜잭션 수행
- Outbox 패턴과 `FOR UPDATE SKIP LOCKED`를 결합해 DB 트랜잭션과 Kafka 발행 간 원자성 확보 → **이벤트 발행 성공률 99.9%**
- 멱등성 키 기반 재시도 전략으로 네트워크 장애 상황에서도 **95% 이상 복구율** 구현
- 5분 타임아웃 주문 자동 취소 및 재고·포인트 자동 복구

### Redis Sorted Set 기반 고가용성 대기열
- 대규모 트래픽 DB 병목 해소를 위해 Redis Sorted Set 기반 대기열/활성열 구조 도입
- 전용 Executor(Thread Pool)와 CompletableFuture로 공용 스레드 풀 간섭 제거 및 비동기 처리 최적화
- User Index Key 관리로 **1인 1토큰 원칙** 보장 및 중복 진입 원천 차단

### 낙관적 락 + Redis 선제어 타임딜 재고 관리
- Optimistic Lock으로 재고 수정 시 정합성 유지, Redis 선제어로 불필요한 DB 요청 사전 차단
- Redis Sorted Set 기반 스케줄러 큐로 타임딜 시작/종료 자동화
- 재고 이벤트 로그(`p_stock_log`)로 모든 재고 변동 이력 추적

### CQRS 및 2-Tier 캐싱 조회 성능 최적화
- CQRS 적용 및 2-Tier 캐싱 전략으로 주문 조회 성능 **50배 향상 (500ms → 10ms)**
- Cache Hit Rate 85~90% 유지로 시스템 처리 효율 극대화

### PortOne 연동 결제 서비스
- PortOne 기반 결제 파이프라인 구축으로 결제 준비·완료·취소 구현
- 포인트 차감 후 잔액(`finalAmount`)을 결제 금액 기준으로 검증해 이중 과금 방지
- 결제 취소 시 Kafka 이벤트 발행으로 비동기 보상 트랜잭션 수행
- 결제 완료 후 7일 자동 구매확정 스케줄러

### 상품 이미지 업로드 및 셀러 대시보드
- MinIO(S3 호환) 기반 이미지 업로드: `POST /api/v1/products/images` (multipart) → 상품에 `imageUrl` 연결
- 셀러 전용 대시보드(`/seller`): 진행중·예정·마감 타임딜 KPI, 누적·7일 매출 및 주문 집계, 재고 부족(10개 미만) 경보, 최근 타임딜·상품 5개 목록

### 포인트 시스템
- 포인트 적립·차감·환불을 Kafka 이벤트로 연동해 도메인 간 결합도 최소화
- **Redisson 분산 락**으로 동시 요청에서도 포인트 데이터 무결성 보장
- USE_PENDING → 결제 확정 시 EARN_CONFIRM, 취소 시 USE_CANCEL 상태 전이

### Elasticsearch 기반 한국어 타임딜 검색
- nori 형태소 분석기로 한국어 토큰화, 타임딜·상품·회사명 통합 검색
- 필드별 boost (제목 2.0 > 상품명 1.5 > 회사명 1.2 > 설명 0.8) + `<mark>` 하이라이트
- ES completion suggester로 자동완성, debounce 200ms로 입력 중 미리보기
- Spring 이벤트(`TimeDealCreatedEvent`/`UpdatedEvent`/`StartedEvent`/`EndedEvent`) → @TransactionalEventListener로 인덱스 동기화
- 부트스트랩 reindexer로 인덱스가 DB보다 뒤처지면 앱 시작 시 자동 보정

### 알림 서비스 분리 + 실시간 WebSocket 푸시
- 독립 `notification-service`(마이크로서비스 분리) + STOMP 기반 WebSocket(`/api/v1/notifications/ws`) 푸시
- Kafka 이벤트(`order.created`, `order.paid`, `order.cancelled`, `user.account.event`, `timedeal.start.notify`, `timedeal.ending.soon`, `timedeal.sold.out.notify`) 구독 → 사용자별 알림 row 생성 + WS push
- WebSocket CONNECT 헤더의 JWT를 ChannelInterceptor로 검증, `/user/queue/notifications`로 사용자별 라우팅
- 14종 알림 타입 (주문/결제/계정/타임딜 진행/매진/관심/셀러)

### 관심 타임딜 + 시작 알림 fanout
- 비회원이 하트 아이콘으로 관심 등록 → 회원가입 유도 funnel
- 타임딜 시작 시 관심 사용자 + 셀러에게 자동 알림 (Kafka fanout 패턴)
- 종료 임박(10분 전) 스케줄러로 사용자 재참여 유도, 매진 발생 시 셀러에게 완판 알림

### 관리자 감사 로그
- 정지/해제/역할변경/삭제 시 `p_admin_audit_log`에 누가·언제·무엇을·누구에게 기록
- 응답 시점에 관리자/대상 이메일을 배치 조회로 enrichment
- 관리자 페이지에서 액션별 필터 + 기간 + 이메일 검색

---

## 🧑‍🤝‍🧑 팀원 및 역할

| 이름 | 담당 업무 |
|------|-----------|
| [변영재 (팀장)](https://github.com/bbangjae) | 인증 및 인가, 사용자, 포인트 |
| [김민수](https://github.com/Doritosch) | 결제, 모니터링 |
| [민송경](https://github.com/miiiiiin) | 대기열, 배포 |
| [유민아](https://github.com/minahYu) | 상품, 타임딜, 재고 |
| [차초희](https://github.com/chachohee) | 주문, 검색·알림·관심·감사로그·테스트 자동화·Flyway·모니터링 대시보드 |

---

## 🛠 기술 스택

### Back-End
| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.8, Spring Security, Spring WebSocket(STOMP) |
| Data | Spring Data JPA, Spring Data Redis, Spring Data Elasticsearch, PostgreSQL |
| Search | Elasticsearch 8.13 + analysis-nori |
| Migration | Flyway |
| Messaging | Spring Kafka |
| Service Discovery | Spring Cloud Eureka, Spring Cloud Gateway |
| External API | OpenFeign, PortOne |
| Resilience | Resilience4j (Circuit Breaker, Retry, Time Limiter) |
| Test | JUnit 5, Testcontainers (Postgres / Kafka / Redis / Elasticsearch), Awaitility |

### Front-End ([rush-deal-web](https://github.com/chachohee/rush-deal-web))
| 분류 | 기술 |
|------|------|
| Framework | Next.js 16 (Turbopack), React 19, TypeScript |
| Styling | Tailwind CSS |
| State / Data | Zustand (persist), TanStack Query |
| Forms | React Hook Form + Zod |
| Realtime | @stomp/stompjs (WebSocket) |
| Payment | @portone/browser-sdk |

### Infrastructure
| 분류 | 기술 |
|------|------|
| Cloud | AWS ECS (Fargate), RDS (PostgreSQL), ElastiCache (Redis), MSK (Kafka), ECR |
| Container | Docker, Docker Compose |
| CI/CD | GitHub Actions, AWS CodeDeploy |

### Monitoring
| 분류 | 기술 |
|------|------|
| Metrics | Prometheus, Grafana (대시보드 3종: 서비스 헬스 / Kafka / 비즈니스 지표) |
| Tracing | Zipkin |
| Kafka UI | Kafka UI (provectuslabs) |

---

## 🧩 DDD 구조 (Bounded Context · Aggregate)

각 마이크로서비스는 독립된 Bounded Context로 설계되어 있으며, 서비스 간 직접 DB 참조 없이 이벤트(Kafka)와 API(Feign)로만 통신합니다.

![ddd](docs/image/ddd.svg)

### Aggregate 요약

| Bounded Context | Aggregate Root | 하위 Entity | Value Object |
|----------------|----------------|-------------|--------------|
| **Auth** | RefreshToken *(Redis)* | — | TokenId, UserId, TokenExpiry |
| **User** | User | — | UserRole |
| **User** | ShippingAddress | — | RecipientName, RecipientPhone, ZipCode, Address |
| **User** | PointHistory | — | Point, UserId, OrderId, SagaId |
| **User** | AdminAuditLog | — | AdminAction |
| **Product** | Product | ProductOption | SellerId, ProductInfo, Price, Category |
| **TimeDeal** | TimeDeal | TimeDealProduct | TimeDealInfo, Price, Period, LimitQuantity |
| **TimeDeal** | TimeDealStock | StockLog | StockCounts, ProductItemIds, Quantity |
| **TimeDeal** | InterestedDeal | — | UserId, TimeDealId |
| **TimeDeal** | TimeDealDocument *(Elasticsearch)* | — | korean-analyzed title/description, completion suggest |
| **Order** | Order | OrderItem, OrderReservation, OrderHistory | OrderAmount, ShippingInfo, ProductSnapshot |
| **Order** | SagaInstance | SagaStep | SagaStatus |
| **Order** | OutboxEventEntity *(인프라)* | — | OutboxStatus |
| **Payment** | Payment | PaymentTransaction | Amount, Card, Cancel |
| **Queue** | QueuePolicy *(DB)* | — | TimePeriod, TrafficSetting |
| **Queue** | QueueToken *(Redis)* | — | TokenId, QueueStatus |
| **Notification** | Notification | — | NotificationType (14종) |

> ★ = Aggregate Root  |  Optimistic Lock: `TimeDealStock.version`  |  분산 락: `PointHistory` (Redisson)

---

## 🏗 시스템 아키텍처

![architecture](docs/image/architecture.png)

---

## 🔁 주문 생성 플로우차트

Saga·Outbox 패턴 기반의 주문 생성 전체 흐름입니다.

![flowchart](docs/image/flowchart.svg)

| 단계 | 설명 |
|------|------|
| **대기열 토큰 검증** | Order Service에서 Queue Service REST 호출로 대기열 토큰 유효성 검증 (JWT 검증은 API Gateway에서 수행) |
| **Saga 시작** | SagaInstance 생성(RUNNING), Order 생성(PENDING) |
| **Outbox 발행** | 5s 폴링 + FOR UPDATE SKIP LOCKED로 Kafka 발행, 실패 시 최대 3회 재시도 |
| **재고 예약** | TimeDeal Service - Optimistic Lock 기반 재고 예약 |
| **포인트 차감** | User Service - Redisson 분산 락 기반 포인트 차감 |
| **보상 트랜잭션** | 어느 한 Step 실패 시 완료된 Step 역순 보상 |
| **결제** | Payment Service - PortOne 웹훅으로 결제 완료 처리 |
| **구매확정** | 결제 후 7일 자동 구매확정 스케줄러 |
| **알림** | 단계별 Kafka 이벤트 → notification-service가 사용자/셀러 알림 생성 + WebSocket 푸시 |

---

## 🗄 ERD

서비스별 독립 스키마로 분리되어 있으며, 서비스 간 DB 직접 참조는 없습니다. 스키마 변경은 **Flyway 마이그레이션 파일**(`src/main/resources/db/migration/V*__*.sql`)로 명시적으로 관리됩니다.

![erd](docs/image/rushdeal_erd.svg)

| 스키마 | 테이블 | 설명 |
|--------|--------|------|
| `user_schema` | p_user, p_point_history, p_shipping_address, **p_admin_audit_log** | 사용자, 포인트, 배송지, 관리자 감사 로그 |
| `product_schema` | p_product, p_product_option | 상품 및 옵션 |
| `time_deal_schema` | p_time_deal, p_time_deal_product, p_time_deal_stock, p_stock_log, **p_interested_deal** | 타임딜, 재고, 재고 이력, 관심 등록 |
| `order_schema` | p_order, p_order_item, p_order_reservation, p_order_history, p_saga_instance, p_saga_step, p_outbox_event | 주문, Saga, Outbox |
| `payment_schema` | p_payment, p_payment_transaction | 결제, 결제 트랜잭션 |
| `queue_schema` | p_queue_policy | 대기열 정책 (토큰은 Redis 전용) |
| `notification_schema` | **p_notification** | 사용자 알림 (Kafka fanout 결과) |
| `auth` | — | JWT 토큰은 Redis 전용 (DB 없음) |
| Elasticsearch `timedeal` index | — | 타임딜 검색용 비정규화 문서 |

---

## 📊 성능 검증

### 동시 주문 부하 테스트

| 테스트 규모 | 성공률 | P95 응답시간 | 처리량 | 데이터 정합성 |
|------------|--------|------------|--------|--------------|
| **100명** | 73% | 2.51초 | 38.9 RPS | 100% ✅ |
| **1,000명** | 75.30% | 6.01초 | 154.19 RPS | 100% ✅ |

### 핵심 검증 결과

- ✅ **1,000명 동시 주문**: 753건 성공 (75.30%), 재고·포인트 정합성 100% 보장
- ✅ **큐 시스템**: 1,000명 토큰 발급 100% 성공 (P95 1.38초)
- ✅ **스케일업**: 100명(38.9 RPS) → 1,000명(154.19 RPS), 처리량 4배 향상
- ✅ **자동 취소**: PENDING 주문 5분 타임아웃 자동 취소 및 보상 트랜잭션 100% 성공

### 시연 영상

- [100명 동시 주문 테스트](https://youtu.be/zYQMJPPH7uQ)
- [1,000명 동시 주문 테스트](https://youtu.be/54cJzqk-PPM)

---

## 🧪 테스트 자동화

핵심 비즈니스 흐름을 보호하기 위해 **Testcontainers 기반 통합 테스트**를 작성했습니다. 외부 의존성(Postgres, Kafka, Redis, Elasticsearch)을 실제로 띄워 운영 환경과 동일하게 검증합니다.

| 서비스 | 테스트 수 | 검증 영역 |
|---|---:|---|
| notification-service | 9 | order.* / user.account.event / timedeal.*.notify Kafka fanout, REST API |
| user-service | 3 | block·unblock·changeRole이 audit log + Kafka 이벤트 + DB 상태를 한 트랜잭션으로 |
| queue-service | 4 | 중복 활성 정책 거부, STOPPED upsert, time-deal-end 수신 시 STOPPED 전환 |
| timedeal-service | 5 | 관심 등록 idempotent, findUserIdsByTimeDealId, 참조 무결성 |
| **합계** | **21** | |

추가 효과로, Flyway 베이스라인의 `NotificationType` 체크 제약이 enum 추가분을 누락한 **silent drift를 자동 발견**하여 `V2__expand_notification_type_check.sql` 마이그레이션을 추가하는 계기가 됐습니다.

---

## 📊 모니터링 대시보드

`http://localhost:3000` (Grafana, 기본 계정 `.env`의 `GRAFANA_ADMIN_*`)로 접근. 자동 프로비저닝된 대시보드 3종:

1. **RushDeal — 서비스 헬스 & JVM**: 11개 서비스 가용성 / HTTP 처리율·에러율 / 응답시간 p95·p99 / JVM 힙·GC / Hikari 커넥션
2. **RushDeal — Kafka 메시징**: 컨슈머 처리율·lag / 프로듀서 송신·에러 / @KafkaListener 처리 시간·실패
3. **RushDeal — 비즈니스 지표**: 가입·주문·검색·관심 등록 분당 / 검색 p95 / 알림 fanout 처리율 / 주문·결제 토픽 처리율 / 5xx·4xx 합계

Prometheus 타겟: 11개 (api-gateway, discovery-service, auth/user/order/payment/product/queue/timedeal/notification-service, prometheus 자체).

---

## 📂 프로젝트 구조

```
rush-deal/
├── api-gateway/          # 요청 라우팅, JWT 인증, 하위 서비스 헤더 전파 (X-User-Id/Role)
├── discovery-service/    # 서비스 디스커버리 (Eureka)
├── auth-service/         # 인증, JWT 발급·갱신·블랙리스트
├── user-service/         # 사용자 관리, 포인트, 관리자 감사 로그
├── product-service/      # 상품 및 옵션 관리
├── timedeal-service/     # 타임딜 생성·관리, 재고 제어, ES 검색, 관심 등록
├── order-service/        # 주문 처리, Saga 오케스트레이션, Outbox
├── payment-service/      # 결제 (PortOne 연동)
├── queue-service/        # Redis 대기열 토큰 발급·관리
├── notification-service/ # 알림 Kafka fanout + WebSocket 푸시
├── monitoring/           # Prometheus 설정 + Grafana 대시보드 3종
├── docs/                 # 아키텍처, ERD, 플로우차트 이미지
├── scripts/              # 테스트 시드 스크립트(seed-test-data.sh) 및 DB 스키마 초기화
├── docker-compose-app.yml   # 로컬 전체 실행 (인프라 + 앱 + 모니터링)
├── Dockerfile.local         # 로컬 빌드용 (JAR 복사)
├── Dockerfile.elasticsearch # nori plugin 포함한 ES 이미지
├── Dockerfile.template      # CI/CD용 (Docker 내부 빌드)
├── start-local.sh           # 로컬 전체 실행 스크립트
├── stop-local.sh            # 로컬 전체 종료 스크립트
└── LOCAL_SETUP.md           # 로컬 실행 가이드
```
