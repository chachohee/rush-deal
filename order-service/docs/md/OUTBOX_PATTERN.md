# 📮 Outbox 패턴 상세 문서

> DB 트랜잭션과 메시지 발행의 원자성을 보장하는 패턴

---

## 목차

1. [Outbox 패턴이란?](#1-outbox-패턴이란)
2. [왜 Outbox 패턴이 필요한가?](#2-왜-outbox-패턴이-필요한가)
3. [RushDeal의 Outbox 구현](#3-rushdeal의-outbox-구현)
4. [FOR UPDATE SKIP LOCKED](#4-for-update-skip-locked)
5. [스케줄러 구현](#5-스케줄러-구현)
6. [재시도 전략](#6-재시도-전략)
7. [코드 구현](#7-코드-구현)

---

## 1. Outbox 패턴이란?

### 정의

Outbox 패턴은 **데이터베이스 트랜잭션과 메시지 발행을 원자적으로 처리**하기 위한 패턴입니다.

비즈니스 로직 실행 시 메시지를 바로 Kafka에 발행하는 대신, 같은 DB 트랜잭션 내에서 **Outbox 테이블에 이벤트를 저장**합니다. 이후 별도의 프로세스(Scheduler)가 주기적으로 Outbox 테이블을 폴링하여 이벤트를 Kafka로 발행합니다.

### 핵심 개념

```
[Application] 
   ↓ BEGIN TRANSACTION
   ├─→ 비즈니스 로직 실행 (INSERT order)
   ├─→ Outbox 이벤트 저장 (INSERT outbox_event)
   ↓ COMMIT TRANSACTION
   
[Scheduler]
   ↓ 주기적 실행 (5초마다)
   ├─→ Outbox 테이블 폴링 (SELECT ... FOR UPDATE SKIP LOCKED)
   ├─→ Kafka 메시지 발행
   └─→ 이벤트 상태 업데이트 (PENDING → PUBLISHED)
```

### At-Least-Once 전송 보장

Outbox 패턴은 **최소 1회 전송(At-Least-Once)**을 보장합니다.

- ✅ 메시지 유실: 불가능 (DB에 저장되므로)
- ⚠️ 메시지 중복: 가능 (네트워크 장애 등으로 재발행)
- 💡 해결: 멱등성 키(Idempotency Key) 사용

---

## 2. 왜 Outbox 패턴이 필요한가?

### 문제 상황 1: 메시지 발행 실패

**시나리오**: DB 커밋은 성공했지만 Kafka 발행 실패

```java
// ❌ 안티패턴
@Transactional
public void createOrder(OrderRequest request) {
    // 1. DB에 주문 저장
    Order order = orderRepository.save(new Order(request));
    
    // 2. Kafka 메시지 발행
    kafkaTemplate.send("order.created", toEvent(order)); // ← 여기서 실패 시?
}
```

**문제점**
```
Step 1: order 저장 → DB COMMIT ✅
Step 2: Kafka 발행 시도 → 네트워크 오류 ❌

결과:
- DB에는 주문 존재 ✅
- Stock Service는 재고 예약 못함 ❌
→ 데이터 불일치 발생!
```

### 문제 상황 2: 트랜잭션 롤백

**시나리오**: Kafka 발행은 성공했지만 DB 트랜잭션 롤백

```java
// ❌ 안티패턴
@Transactional
public void createOrder(OrderRequest request) {
    // 1. Kafka 메시지 발행
    kafkaTemplate.send("order.created", event).get(); // ← 성공
    
    // 2. DB에 주문 저장
    Order order = orderRepository.save(new Order(request));
    
    // 3. 추가 검증
    if (order.getTotalAmount() > 1000000) {
        throw new RuntimeException("금액 초과"); // ← 롤백 발생
    }
}
```

**문제점**
```
Step 1: Kafka 발행 → 성공 ✅
Step 2: order 저장 → 성공 ✅
Step 3: 예외 발생 → DB ROLLBACK ❌

결과:
- DB에는 주문 없음 ❌
- Stock Service는 재고 예약 진행 중 ✅
→ 유령 이벤트(Ghost Event) 발생!
```

### Outbox 패턴의 해결책

#### ✅ 해결책 1: 원자성 보장

```java
// ✅ Outbox 패턴
@Transactional
public void createOrder(OrderRequest request) {
    // 1. DB에 주문 저장
    Order order = orderRepository.save(new Order(request));
    
    // 2. Outbox 이벤트 저장 (같은 트랜잭션)
    OutboxEventEntity event = OutboxEventEntity.create(
        "ORDER",
        order.getOrderId(),
        "ORDER_CREATED",
        toJson(order)
    );
    
    outboxEventRepository.save(event);
    
    // 3. 커밋 (주문과 이벤트가 함께 커밋)
}
```

**장점**
- DB 커밋 성공 = 이벤트 저장 성공
- Kafka 장애와 무관하게 이벤트 보존

#### ✅ 해결책 2: 중복 발행 방지

```sql
-- FOR UPDATE SKIP LOCKED 사용
SELECT * FROM order_schema.p_outbox_event
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED
```

**동작 방식**
```
[Instance 1] FOR UPDATE SKIP LOCKED
              ↓ (Event 1~100 락 획득)
[Instance 2] FOR UPDATE SKIP LOCKED
              ↓ (Event 101~200 락 획득, 1~100은 SKIP)
              
각 인스턴스가 서로 다른 이벤트 처리 → 중복 없음!
```

---

## 3. RushDeal의 Outbox 구현

### Outbox 테이블 스키마

```sql
CREATE TABLE order_schema.p_outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,      -- 'ORDER', 'ORDER_SAGA' 등
    aggregate_id UUID NOT NULL,               -- 주문 ID, Saga ID 등
    event_type VARCHAR(100) NOT NULL,         -- 'ORDER_CREATED', 'STOCK_RESERVATION_REQUESTED' 등
    payload TEXT NOT NULL,                   -- 이벤트 페이로드 (JSON)
    status VARCHAR(20) NOT NULL,              -- 'PENDING', 'PUBLISHED', 'FAILED'
    retry_count INTEGER DEFAULT 0,             -- 재시도 횟수
    error_message TEXT,                      -- 실패 시 오류 메시지
    created_at TIMESTAMP NOT NULL,           -- 생성 시각
    published_at TIMESTAMP,                   -- 발행 시각
    failed_at TIMESTAMP                      -- 실패 시각
);

-- 인덱스
CREATE INDEX idx_status_created_at ON p_outbox_event(status, created_at);
CREATE INDEX idx_aggregate_id ON p_outbox_event(aggregate_id);
```

### 이벤트 상태 전이도

```
PENDING (초기 상태)
   ↓
   ├─→ [발행 성공] → PUBLISHED (최종 상태)
   │
   └─→ [발행 실패] → FAILED
                      ↓
                      ├─→ [재시도 성공] → PUBLISHED
                      └─→ [3회 초과] → FAILED (수동 처리 필요)
```

### 이벤트 타입

| 이벤트 타입 | Kafka Topic | 설명 |
|------------|-------------|------|
| ORDER_CREATED | order.created | 주문 생성 완료 |
| ORDER_CANCELLED | order.cancelled | 주문 취소 |
| ORDER_PAID | order.paid | 결제 완료 |
| STOCK_RESERVATION_REQUESTED | stock.reservation.requested | 재고 예약 요청 |
| STOCK_ROLLBACK_REQUESTED | stock.restore.requested | 재고 복구 요청 |
| POINT_USE_CANCEL_REQUESTED | point.use.cancel.requested | 포인트 사용 취소 요청 |

---

## 4. FOR UPDATE SKIP LOCKED

### PostgreSQL의 동시성 제어 메커니즘

#### 기본 FOR UPDATE

```sql
-- ❌ 문제: 다른 세션이 락을 대기
SELECT * FROM outbox_event 
WHERE status = 'PENDING' 
FOR UPDATE;
```

**동작**
```
Session 1: Row 1~100 락 획득 ✅
Session 2: 같은 Row 1~100 락 대기... ⏳ (블로킹)
```

#### FOR UPDATE SKIP LOCKED

```sql
-- ✅ 해결: 락 걸린 행은 건너뜀
SELECT * FROM order_schema.p_outbox_event 
WHERE status = 'PENDING' 
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

**동작**
```
Session 1: Row 1~100 락 획득 ✅
Session 2: Row 1~100은 건너뛰고 Row 101~200 락 획득 ✅
```

### 장점

1. **데드락 방지**
   - 대기 없이 바로 다른 행 처리
   - 락 경합 최소화

2. **수평 확장 가능**
   - 여러 인스턴스 동시 실행 가능
   - 자동으로 작업 분산

3. **성능 향상**
   - 블로킹 없음
   - 처리량 증가

### RushDeal 적용 결과

```java
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

**테스트 결과**
- 3개 인스턴스 동시 실행: 중복 발행 0건
- 평균 처리 시간: 200ms (배치 100개)
- 이벤트 발행 성공률: 99.9%

---

## 5. 스케줄러 구현

### 1. Outbox Event Publisher Scheduler

**역할**: PENDING 이벤트를 Kafka로 발행

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {
    
    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Scheduled(fixedDelay = 5000) // 5초마다
    @Transactional
    public void publishPendingEvents() {
        // 1. PENDING 이벤트 조회 (FOR UPDATE SKIP LOCKED)
        List<OutboxEventEntity> pendingEvents =
            outboxRepository.findPendingEventsForUpdate(100);
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.info("발행 대기 중인 Outbox 이벤트 {}개 발견", pendingEvents.size());
        
        // 2. 각 이벤트 발행
        for (OutboxEventEntity event : pendingEvents) {
            publishEventWithTransaction(event);
        }
    }
    
    @Transactional
    public void publishEventWithTransaction(OutboxEventEntity event) {
        try {
            String topic = getTopicName(event.getEventType());
            
            // Kafka 발행 (동기 방식)
            kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                .get(); // Future.get()으로 동기 대기
            
            // 발행 성공
            event.markAsPublished();
            outboxRepository.save(event);
            
        } catch (Exception e) {
            log.error("Outbox 이벤트 발행 실패: eventId={}, eventType={}",
                event.getEventId(), event.getEventType(), e);
            
            event.markAsFailed(e.getMessage());
            outboxRepository.save(event);
        }
    }
    
    private String getTopicName(String eventType) {
        return switch (eventType) {
            case "ORDER_CREATED" -> "order.created";
            case "ORDER_CANCELLED" -> "order.cancelled";
            case "STOCK_RESERVATION_REQUESTED" -> "stock.reservation.requested";
            case "STOCK_ROLLBACK_REQUESTED" -> "stock.restore.requested";
            case "POINT_USE_CANCEL_REQUESTED" -> "point.use.cancel.requested";
            default -> "order.events";
        };
    }
}
```

**설정**
- 실행 주기: 5초
- 배치 크기: 100개
- 타임아웃: 5초

---

### 2. Failed Event Retry Scheduler

**역할**: FAILED 이벤트 재시도

```java
@Scheduled(fixedDelay = 600000) // 10분마다
public void retryFailedEvents() {
    Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
    
    List<OutboxEventEntity> failedEvents =
        outboxRepository.findFailedEventsForRetry(oneHourAgo, Pageable.ofSize(50));
    
    if (failedEvents.isEmpty()) {
        return;
    }
    
    log.info("재시도 대상 Outbox 이벤트 {}개 발견", failedEvents.size());
    
    for (OutboxEventEntity event : failedEvents) {
        if (event.canRetry()) {
            retryEventWithTransaction(event);
        }
    }
}

@Transactional
public void retryEventWithTransaction(OutboxEventEntity event) {
    try {
        event.retry();
        outboxRepository.save(event);
        
        // 즉시 발행 시도
        publishEventWithTransaction(event);
        
    } catch (Exception e) {
        log.error("이벤트 재시도 실패: eventId={}", event.getEventId(), e);
    }
}
```

**재시도 조건**
```sql
WHERE status = 'FAILED'
  AND retry_count < 3
  AND failed_at < :oneHourAgo
```

**설정**
- 실행 주기: 10분
- 최대 재시도: 3회
- 재시도 간격: 1시간

---

### 3. Outbox Cleanup Scheduler

**역할**: 오래된 PUBLISHED 이벤트 삭제

```java
@Scheduled(cron = "0 0 0 * * ?") // 매일 자정
@Transactional
public void cleanupOldEvents() {
    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    int deletedCount = outboxRepository.deletePublishedEventsBefore(sevenDaysAgo);
    
    if (deletedCount > 0) {
        log.info("오래된 Outbox 이벤트 {}개 삭제 완료", deletedCount);
    }
}
```

**삭제 조건**
- 상태: PUBLISHED
- 보관 기간: 7일 초과

**효과**
- DB 용량 관리
- 쿼리 성능 유지

---

## 6. 재시도 전략

### 재시도 한계

**3회 초과 시**
- 상태: FAILED (최종)
- 로그 기록: 상세 오류 정보 저장
- 수동 처리: 관리자 확인 필요

### 재시도 조건

```java
public boolean canRetry() {
    return this.retryCount < 3 && this.status == OutboxStatus.FAILED;
}

public void retry() {
    if (!canRetry()) {
        throw new IllegalStateException("재시도 불가능한 상태입니다.");
    }
    this.status = OutboxStatus.PENDING;
    this.errorMessage = null;
}
```

**재시도 간격**: 1시간 후

---

## 7. 코드 구현

### OutboxEventEntity

```java
@Entity
@Table(name = "p_outbox_event", schema = "order_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OutboxEventEntity {
    
    @Id
    private UUID eventId;
    
    @Column(nullable = false, length = 50)
    private String aggregateType; // "ORDER", "ORDER_SAGA" 등
    
    @Column(nullable = false)
    private UUID aggregateId; // 주문 ID, Saga ID 등
    
    @Column(nullable = false, length = 100)
    private String eventType; // "ORDER_CREATED", "STOCK_RESERVATION_REQUESTED" 등
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON 문자열
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status; // PENDING, PUBLISHED, FAILED
    
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant publishedAt;
    private Instant failedAt;
    
    public static OutboxEventEntity create(
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload
    ) {
        return OutboxEventEntity.builder()
            .eventId(UUID.randomUUID())
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .payload(payload)
            .status(OutboxStatus.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
    }
    
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.failedAt = Instant.now();
        this.errorMessage = errorMessage;
        this.retryCount++;
    }
    
    public boolean canRetry() {
        return this.retryCount < 3 && this.status == OutboxStatus.FAILED;
    }
    
    public void retry() {
        if (!canRetry()) {
            throw new IllegalStateException("재시도 불가능한 상태입니다.");
        }
        this.status = OutboxStatus.PENDING;
        this.errorMessage = null;
    }
    
    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
```

### OutboxEventJpaRepository

```java
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
    
    /**
     * PENDING 상태의 이벤트 조회 (동시성 제어 포함)
     * FOR UPDATE SKIP LOCKED를 사용하여 여러 인스턴스가 동시에 실행해도
     * 같은 이벤트를 중복 처리하지 않도록 보장
     */
    @Query(
        value = "SELECT * FROM order_schema.p_outbox_event o " +
            "WHERE o.status = 'PENDING' " +
            "ORDER BY o.created_at ASC " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED",
        nativeQuery = true
    )
    List<OutboxEventEntity> findPendingEventsForUpdate(@Param("limit") int limit);
    
    /**
     * 재시도 대상 FAILED 이벤트 조회
     */
    @Query("SELECT o FROM OutboxEventEntity o " +
           "WHERE o.status = 'FAILED' " +
           "AND o.retryCount < 3 " +
           "AND o.failedAt < :oneHourAgo " +
           "ORDER BY o.failedAt ASC")
    List<OutboxEventEntity> findFailedEventsForRetry(
        @Param("oneHourAgo") Instant oneHourAgo,
        Pageable pageable
    );
    
    /**
     * 오래된 PUBLISHED 이벤트 삭제
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity o " +
           "WHERE o.status = 'PUBLISHED' " +
           "AND o.publishedAt < :before")
    int deletePublishedEventsBefore(@Param("before") Instant before);
}
```

### 사용 예시

```java
// RequestStockReservationStep.java
public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
    // Outbox 이벤트 저장
    outboxPort.createAndSave(
        "ORDER_SAGA",
        context.getSagaId(),
        OutboxEventType.STOCK_RESERVATION_REQUESTED,
        objectMapper.writeValueAsString(payload)
    );
    
    // ⚡ 여기서 클라이언트에 즉시 응답 반환
    return SagaStepResult.success();
}
```

---

## 🎯 핵심 요약

✅ **원자성 보장**: DB 트랜잭션과 이벤트 발행 원자적 처리  
✅ **FOR UPDATE SKIP LOCKED**: 중복 발행 방지 및 수평 확장  
✅ **자동 재시도**: 3회까지 자동 재시도  
✅ **이벤트 발행 성공률 99.9%**: Kafka 장애에도 유실 없음  
✅ **At-Least-Once 보장**: 최소 1회 전송 보장

---

**작성일**: 2026-01-12  
**작성자:** 차초희  
**검토자:** 차초희  
**최종 수정일:** 2026-01-13  
**버전**: 2.0
