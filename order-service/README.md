# 📦 Order Service

> Saga 패턴과 Outbox 패턴 기반의 고신뢰성 분산 주문 처리 서비스

## 목차

1. [서비스 개요](#-서비스-개요)
2. [핵심 기능](#-핵심-기능)
3. [아키텍처 패턴](#-아키텍처-패턴)
4. [기술 스택](#-기술-스택)
5. [주문 플로우](#-주문-플로우)
6. [동시성 제어](#-동시성-제어)
7. [스케줄러](#-스케줄러)
8. [성능 최적화](#-성능-최적화)
9. [테스트](#-테스트)
10. [API 명세](#-api-명세)

---

## 📌 서비스 개요

Order Service는 RushDeal 프로젝트의 핵심 서비스로, 대규모 트래픽 환경에서 안정적인 주문 처리를 담당합니다.

### 주요 책임

- **주문 생성 및 관리**: 사용자의 주문 요청을 받아 검증하고 처리
- **분산 트랜잭션 관리**: Saga 패턴을 통한 서비스 간 트랜잭션 조율
- **재고-포인트 연동**: 비동기 이벤트 기반 도메인 간 협업
- **주문 상태 관리**: 생성부터 취소/완료까지 전체 라이프사이클 관리
- **자동 취소 처리**: 미결제 주문 자동 감지 및 보상 트랜잭션 실행

### 설계 목표

✅ **고가용성**: 99.9%의 이벤트 발행 성공률  
✅ **데이터 정합성**: 100% 재고 및 포인트 정합성 유지  
✅ **확장성**: MSA 기반 수평 확장 가능 구조  
✅ **성능**: 100명 동시 주문 처리 (P95 응답시간 2.5초 이내)  
✅ **장애 복구**: 자동 재시도 및 보상 트랜잭션으로 95% 이상 복구율

---

## 🔑 핵심 기능

### 1. Saga 패턴 기반 분산 트랜잭션

**Orchestration 방식의 Saga 구현**

Order Service가 Saga Orchestrator 역할을 수행하며, 다음 단계를 순차적으로 조율합니다:

```
1. ValidateStock      → 재고 검증 (읽기 전용)
2. CheckPurchaseLimit → 구매 제한 검증 (1인당 5개)
3. UsePoint           → 포인트 차감 (동기 처리)
4. RequestStockReservation → 재고 예약 요청 (비동기, Kafka)
   └─→ 클라이언트에 즉시 응답 반환 (약 2초) ⚡
5. StockReservedEvent → 재고 예약 완료 이벤트 수신 (비동기)
6. CreateOrder        → 주문 생성 (비동기)
```

**보상 트랜잭션 (Compensating Transaction)**

- 구매 제한 초과: 포인트 차감 없이 즉시 실패 응답
- 재고 예약 실패: 포인트 환불 (USE_CANCEL)
- 주문 생성 실패: 재고 복구 + 포인트 환불

**효과**
- 비동기 실행으로 응답 시간 **59% 단축** (5초 → 2초)
- 서비스 간 느슨한 결합 (Loose Coupling)
- 장애 격리 (Fault Isolation)

---

### 2. Outbox 패턴을 통한 이벤트 발행 신뢰성

**문제점**
- DB 트랜잭션 커밋과 Kafka 메시지 발행 사이의 원자성 보장 불가
- Kafka 장애 시 이벤트 유실 가능성

**해결 방안**

```java
// 1. 비관적 락을 활용한 동시성 제어
@Query(
   value = "SELECT * FROM order_schema.p_outbox_event o " +
      "WHERE o.status = 'PENDING' " +
      "ORDER BY o.created_at ASC " +
      "LIMIT :limit " +
      "FOR UPDATE SKIP LOCKED",
   nativeQuery = true
)
List<OutboxEventEntity> findPendingEventsForUpdate(@Param("limit") int limit);
```

**FOR UPDATE SKIP LOCKED의 장점**
- 여러 인스턴스에서 동시 실행 가능
- 락을 획득한 행만 조회, 이미 락이 걸린 행은 건너뜀
- 데드락 없이 안전한 동시성 제어
- 중복 이벤트 발행 방지

**스케줄러 기반 자동 발행**

```yaml
주기:
  - PENDING 이벤트 발행: 5초마다
  - FAILED 이벤트 재시도: 10분마다
  - 오래된 이벤트 정리: 매일 자정

재시도 전략:
  - 최대 재시도: 3회
  - 재시도 간격: 1시간 후
  - 7일 이상 지난 PUBLISHED 이벤트 자동 삭제
```

**효과**
- ✅ Kafka 장애 시에도 이벤트 유실 방지
- ✅ At-Least-Once 전송 보장
- ✅ 자동 재시도로 시스템 복원력 향상
- ✅ **이벤트 발행 성공률 99.9% 달성**

---

### 3. CQRS 패턴 적용

**Command Query Responsibility Segregation**

쓰기(Command)와 읽기(Query)를 완전히 분리하여 각각 최적화:

```
Command Side (쓰기)
├── OrderCommandController
├── OrderCommandService
└── PostgreSQL (정합성 중시)

Query Side (읽기)
├── OrderQueryController
├── OrderQueryService
└── Redis Cache (성능 중시)
```

**구현 상세**

```java
// Command Side - 주문 생성/수정/취소
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCommandController {
    // POST /api/v1/orders - 주문 생성
    // PATCH /api/v1/orders/{id} - 주문 수정
    // DELETE /api/v1/orders/{id} - 주문 취소
}

// Query Side - 주문 조회
@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {
    // GET /api/v1/orders/{id} - 주문 상세 조회
    // GET /api/v1/orders - 주문 목록 조회
}
```

**효과**
- ✅ 읽기 성능 최적화: Redis 캐시로 조회 속도 향상
- ✅ 쓰기 성능 최적화: 복잡한 조인 없이 단순 저장
- ✅ 확장성: 읽기/쓰기 DB 분리 가능
- ✅ 캐시 무효화: 주문 상태 변경 시 자동 캐시 갱신

---

### 4. 2-Tier 캐싱 전략

**Local Cache (L1) + Redis Cache (L2)**

```java
@Cacheable(value = "order", key = "#orderId")
public OrderDetailDto getOrderDetail(String orderId) {
    // 1순위: Local Cache (Caffeine)
    // 2순위: Redis Cache
    // 3순위: Database
}
```

**캐시 계층별 특성**

| 계층 | 저장소 | TTL | 용도 |
|------|--------|-----|------|
| L1 | Caffeine | 5분 | 초고속 조회 (동일 인스턴스 내) |
| L2 | Redis | 1시간 | 분산 환경 조회 (전체 인스턴스) |
| DB | PostgreSQL | - | 원본 데이터 |

**캐시 무효화 전략**

```java
@CacheEvict(value = "order", key = "#orderId")
public void updateOrderStatus(String orderId, OrderStatus status) {
    // 주문 상태 변경 시 캐시 자동 삭제
}
```

**성능 결과**
- 조회 성능 **50배 향상** (500ms → 10ms)
- Cache Hit Rate **85~90%** 유지
- DB 부하 **70% 감소**

---

## 🏗 아키텍처 패턴

### Saga Pattern (Orchestration)

```mermaid
sequenceDiagram
    participant Client
    participant Order
    participant Point
    participant Stock
    participant Kafka

    Client->>Order: 주문 요청
    Order->>Order: 1. 재고 검증 (ValidateStock)
    Order->>Order: 2. 구매 제한 체크
    Order->>Point: 3. 포인트 차감 (UsePoint)
    Point-->>Order: 차감 완료
    Order->>Kafka: 4. 재고 예약 요청 발행
    Order-->>Client: 즉시 응답 (SagaId, PENDING)
    
    Note over Order,Kafka: 비동기 처리 시작
    
    Kafka->>Stock: 재고 예약 요청 전달
    Stock->>Stock: 재고 차감
    Stock->>Kafka: 예약 완료 이벤트 발행
    Kafka->>Order: 예약 완료 수신
    Order->>Order: 5. 주문 생성 (CreateOrder)
    
    Note over Order: Saga 완료
```

### Outbox Pattern

```mermaid
sequenceDiagram
    participant App as Application
    participant DB as PostgreSQL
    participant Scheduler
    participant Kafka

    App->>DB: BEGIN TRANSACTION
    App->>DB: INSERT order
    App->>DB: INSERT outbox_event (PENDING)
    App->>DB: COMMIT
    
    Note over Scheduler: 5초마다 실행
    
    Scheduler->>DB: SELECT * FOR UPDATE SKIP LOCKED
    DB-->>Scheduler: PENDING events
    
    loop 각 이벤트
        Scheduler->>Kafka: Publish event
        alt 발행 성공
            Scheduler->>DB: UPDATE status = PUBLISHED
        else 발행 실패
            Scheduler->>DB: UPDATE status = FAILED, retry_count++
        end
    end
```

### CQRS Pattern

```mermaid
flowchart TB
    subgraph ORDER["Order Service"]
        direction TB

        subgraph WRITE["Write Model (Command Side)"]
            direction TB
            A1["주문 생성"]
            A2["주문 수정"]
            A3["주문 취소"]
            A_DB[(PostgreSQL)]
        end

        subgraph READ["Read Model (Query Side)"]
            direction TB
            B1["주문 조회"]
            B2["주문 목록"]
            B3["상세 정보"]
            B_CACHE[(Redis)]
        end

        WRITE -->|Event: 주문 생성/수정| READ
    end
```

---

## 🛠 기술 스택

### Core Framework
- **Java 21**: 최신 LTS 버전, Virtual Threads 활용
- **Spring Boot 3.5.8**: 최신 Spring 생태계
- **Spring Data JPA**: ORM 및 데이터 접근 계층

### Messaging & Event
- **Spring Kafka**: 비동기 메시지 처리
- **Kafka**: 이벤트 스트리밍 플랫폼

### Cache & Storage
- **Spring Data Redis**: 캐싱 및 대기열
- **PostgreSQL**: 주 데이터베이스

### Service Communication
- **OpenFeign**: 동기 서비스 간 통신 (User Service)
- **Kafka**: 비동기 서비스 간 통신 (Stock Service)

### Monitoring
- **Micrometer**: 메트릭 수집
- **Zipkin**: 분산 추적

---

## 🔄 주문 플로우

### 1. 주문 생성 플로우

```
[사용자] 
  ↓ 주문 요청
[Queue Service] 
  ↓ 토큰 검증
[Order Service]
  ├─→ 1. 재고 검증 (읽기)
  ├─→ 2. 구매 제한 체크 (5개/인)
  ├─→ 3. 포인트 차감 (동기, User Service)
  ├─→ 4. Outbox 이벤트 저장 (PENDING)
  └─→ 응답 반환 (SagaId, PENDING)
        ↓
  [Outbox Scheduler]
  ├─→ 5. Kafka 이벤트 발행 (stock.reservation.requested)
  └─→ Outbox 상태 업데이트 (PUBLISHED)
        ↓
  [Stock Service]
  └─→ 6. 재고 예약 처리
        ↓
  [Kafka]
  └─→ 7. 예약 완료 이벤트 (stock.reserved)
        ↓
  [Order Service]
  └─→ 8. 주문 생성 (PENDING → PENDING)
```

### 2. 주문 자동 취소 플로우

```
[PendingOrderTimeoutScheduler]
  ├─→ 1. PENDING 상태 5분 초과 주문 조회
  ├─→ 2. 주문 상태 변경 (PENDING → CANCELLED)
  └─→ 3. Outbox 이벤트 저장 (order.cancelled)
        ↓
  [Outbox Scheduler]
  └─→ 4. Kafka 이벤트 발행
        ↓
  [Stock Service]
  └─→ 5. 재고 복구 (Reserved → Available)
        ↓
  [User Service]
  └─→ 6. 포인트 환불 (USE_CANCEL)
```

### 3. 결제 완료 플로우

```
[Payment Service]
  └─→ 결제 완료 이벤트 (payment.completed)
        ↓
  [Order Service]
  └─→ 주문 상태 변경 (PENDING → COMPLETED)
```

---

## 🔒 동시성 제어

### 1. 재고 동시성 제어

**낙관적 락 (1차 시도)**

```java
@Entity
@Table(name = "p_time_deal_stock")
public class TimeDealStock {
    @Version
    private Long version;
}
```

**비관적 락 (2차 시도, 경합 발생 시)**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM TimeDealStock s WHERE s.id = :stockId")
Optional<TimeDealStock> findByIdWithLock(@Param("stockId") String stockId);
```

**효과**
- 일반적인 경우: 낙관적 락으로 빠른 처리
- 경합 상황: 비관적 락으로 안전성 보장
- **100명 동시 주문에서 100% 정합성 유지**

### 2. Outbox 동시성 제어

**FOR UPDATE SKIP LOCKED**

```sql
SELECT * FROM order_schema.p_outbox_event
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 10
FOR UPDATE SKIP LOCKED
```

**효과**
- 여러 인스턴스에서 동시 실행 가능
- 중복 이벤트 발행 방지
- 데드락 없는 안전한 처리

---

## ⏰ 스케줄러

Order Service는 **7개의 스케줄러**를 통해 이벤트 발행, 주문 상태 관리, 성능 최적화, 장애 복구를 자동화합니다.

### 주요 스케줄러

| 스케줄러 | 실행 주기 | 역할 | 성공률 |
|----------|-----------|------|--------|
| Outbox Event Publisher | 5초 | PENDING 이벤트 Kafka 발행 | 99.9% |
| Failed Event Retry | 10분 | FAILED 이벤트 재시도 | 95% |
| Outbox Cleanup | 매일 자정 | 오래된 이벤트 삭제 | 100% |
| Pending Order Timeout | 1분 | 타임아웃 주문 자동 취소 | 100% |
| Cache Warming | 시작 시 + 6시간마다 | 인기 주문 캐시 적재 | 100% |
| Cache Cleanup | 매일 새벽 2시 | 오래된 캐시 삭제 | 100% |
| Auto Confirm | 1분 | 자동 구매 확정 | 100% |
| Saga Recovery | 10분 | 타임아웃 Saga 복구 | 100% |

**→ [스케줄러 상세 문서](docs/md/SCHEDULER.md)** - 각 스케줄러의 구현, 설정, 성능 지표

---

## ⚡ 성능 최적화

### 1. 비동기 처리

**Before (동기 방식)**
```
주문 생성 → 재고 예약 (대기) → 포인트 차감 (대기) → 응답
총 소요: 약 5000ms
```

**After (비동기 Saga)**
```
재고 검증 → 포인트 차감 → Kafka 발행 → 응답 (평균 2060ms)
                              ↓
                     백그라운드: 재고 예약 → 주문 생성
```

**개선 효과**

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| 평균 응답시간 | ~5000ms | 2060ms | ⬇️ 59% |
| P95 응답시간 | ~5000ms | 2510ms | ⬇️ 50% |
| 처리량 (TPS) | ~20 req/s | 38.5 req/s | ⬆️ 93% |
| 동시 처리 | 10~20명 | 100명 | ⬆️ 500% |

### 2. 캐싱 전략

**2-Tier 캐싱**

```java
// L1: Local Cache (Caffeine)
@CacheConfig(cacheNames = "order")
public class OrderQueryService {
    
    @Cacheable(key = "#orderId")
    public OrderDetailDto getOrderDetail(String orderId) {
        // L2: Redis Cache
        return orderRepository.findById(orderId)
            .map(this::toDto)
            .orElseThrow();
    }
}
```

**효과**
- 조회 성능: **50배 향상** (500ms → 10ms)
- Cache Hit Rate: **85~90%**
- DB 부하: **70% 감소**

### 3. 데이터베이스 최적화

**인덱스 전략**

```sql
-- 주문 ID 조회 (Primary Key)
CREATE INDEX idx_order_id ON p_order(id);

-- 사용자별 주문 목록
CREATE INDEX idx_order_user_created ON p_order(user_id, created_at DESC);

-- PENDING 주문 조회 (스케줄러)
CREATE INDEX idx_order_status_created ON p_order(status, created_at);

-- Outbox 이벤트 조회
CREATE INDEX idx_outbox_status_created ON p_outbox_event(status, created_at);
```

**커넥션 풀 최적화**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 3000
      idle-timeout: 600000
```

---

## 🧪 테스트

### 주문 플로우 검증 테스트

**테스트 목적**: 대규모 동시 주문 환경에서 주문 플로우 정상 동작 및 데이터 정합성 검증

**테스트 환경**
- 동시 사용자: 100명
- 총 재고: 400개 (4개 옵션 × 100개)
- 상품 가격: 95,200원
- 포인트 사용: 1,000원/건
- 구매 제한: 5개/인

**테스트 시나리오**
- 73% → 정상 주문 (1~5개)
- 27% → 구매 제한 초과 테스트 (6~14개)

**성능 지표**

| 항목 | 목표 | 실제 결과 | 달성률 |
|------|------|-----------|--------|
| 응답시간 (p95) | < 5000ms | 2510ms | ✅ 50.2% |
| 응답시간 (p99) | < 10000ms | 2520ms | ✅ 25.2% |
| 주문 성공률 | > 50% | 73% | ✅ +46% |
| 처리 시간 (p95) | < 8000ms | 2530ms | ✅ 31.6% |
| HTTP 실패율 | < 50% | 27% | ✅ -46%p |

**데이터 정합성**

```
✅ 성공한 주문: 73건 (73.00%)
🎯 구매 제한 초과: 27건 (27.00%) ← 의도된 테스트
❌ 재고 부족: 0건
❌ 포인트 부족: 0건
❌ 시스템 에러: 0건

📦 총 주문 수량: 219개
💰 총 주문 금액: 20,848,800원
⏱️ 평균 처리 시간: 2122ms
```

**재고 정합성**

```
테스트 전: 400개
예약된 재고: 219개
남은 재고: 181개
오차: 0개 ✅

자동 취소 후:
복구된 재고: 400개 (100%)
오차: 0개 ✅
```

**포인트 정합성**

```
주문 서비스: 73건 / 73,000원
포인트 서비스: 73건 / 73,000원
검증 결과: ✅ MATCH (100% 일치)

트랜잭션:
- USE_PENDING: 73건 / 73,000원
- USE_CANCEL: 73건 / 73,000원 (자동 취소 후 환불)
```

### 테스트 문서

상세한 테스트 가이드 및 결과는 아래 문서를 참고하세요:

- **[테스트 실행 가이드](docs/md/ORDER_FLOW_VALIDATION_TEST_GUIDE.md)** - 단계별 테스트 실행 방법
- **[테스트 결과 보고서](docs/md/ORDER_FLOW_VALIDATION_TEST_RESULT.md)** - 상세 검증 결과 및 분석
- **[테스트 결과 요약](docs/md/ORDER_FLOW_VALIDATION_TEST_SUMMARY.md)** - 핵심 성과 지표 및 개선 방향

---

## 📡 API 명세

### Command APIs (쓰기)

#### 1. 주문 생성

```http
POST /api/v1/orders
Content-Type: application/json
X-User-Id: {userId}
X-User-Role: USER
Authorization: Bearer {token}

{
  "timeDealId": "uuid",
  "usePoint": 1000,
  "queueToken": "token",
  "items": [
    {
      "timeDealStockId": "uuid",
      "quantity": 2
    }
  ]
}
```

**Response (Success)**
```json
{
  "success": true,
  "data": {
    "sagaId": "uuid",
    "orderId": "uuid",
    "status": "PENDING"
  }
}
```

**Response (Failure)**
```json
{
  "success": false,
  "error": {
    "code": "PURCHASE_LIMIT_EXCEEDED",
    "message": "구매 수량 제한을 초과했습니다"
  }
}
```

#### 2. 주문 취소

```http
DELETE /api/v1/orders/{orderId}
X-User-Id: {userId}
```

**Response**
```json
{
  "success": true,
  "data": {
    "orderId": "uuid",
    "status": "CANCELLED",
    "cancelledAt": "2026-01-11T21:10:38Z"
  }
}
```

### Query APIs (읽기)

#### 3. 주문 상세 조회

```http
GET /api/v1/orders/{orderId}
X-User-Id: {userId}
```

**Response**
```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "status": "PENDING",
  "totalAmount": 190400,
  "usePoint": 1000,
  "finalAmount": 189400,
  "items": [
    {
      "timeDealStockId": "uuid",
      "productName": "상품명",
      "quantity": 2,
      "price": 95200
    }
  ],
  "createdAt": "2026-01-11T21:04:35Z"
}
```

#### 4. 주문 목록 조회

```http
GET /api/v1/orders?page=0&size=20
X-User-Id: {userId}
```

**Response**
```json
{
  "content": [
    {
      "orderId": "uuid",
      "status": "PENDING",
      "totalAmount": 190400,
      "createdAt": "2026-01-11T21:04:35Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

---

## 🔗 관련 문서

**아키텍처 및 패턴:**
- **[시스템 아키텍처](docs/md/ORDER_ARCHITECTURE.md)** - 주문 시스템 구조
- **[Saga 패턴 상세](docs/md/SAGA_PATTERN.md)** - 분산 트랜잭션 관리
- **[Outbox 패턴 상세](docs/md/OUTBOX_PATTERN.md)** - 이벤트 발행 신뢰성
- **[스케줄러 상세](docs/md/SCHEDULER.md)** - 스케줄러 구현 및 운영

**테스트 문서:**
- **[테스트 실행 가이드](docs/md/ORDER_FLOW_VALIDATION_TEST_GUIDE.md)** - 단계별 테스트 방법
- **[테스트 결과 요약](docs/md/ORDER_FLOW_VALIDATION_TEST_SUMMARY.md)** - 핵심 성과 지표
- **[테스트 결과 상세](docs/md/ORDER_FLOW_VALIDATION_TEST_RESULT.md)** - 상세 검증 결과

---

## 📈 주요 성과

✅ **100명 동시 주문 처리** - P95 응답시간 2.51초 달성  
✅ **이벤트 발행 성공률 99.9%** - Outbox 패턴 적용  
✅ **재고 복구율 100%** - 자동 취소 메커니즘  
✅ **포인트 환불 정확도 100%** - 보상 트랜잭션  
✅ **조회 성능 50배 향상** - CQRS + 2-Tier 캐싱

---

**작성일**: 2026-01-11  
**작성자:** 차초희  
**검토자:** 차초희  
**최종 수정일:** 2026-01-13  
**버전**: 2.0
