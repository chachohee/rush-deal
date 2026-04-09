# ⏰ Order Service 스케줄러

> 이벤트 발행, 주문 상태 관리, 성능 최적화를 자동화하는 스케줄러

---

## 목차

1. [스케줄러 개요](#1-스케줄러-개요)
2. [Outbox 관련 스케줄러](#2-outbox-관련-스케줄러)
3. [주문 상태 관리 스케줄러](#3-주문-상태-관리-스케줄러)
4. [성능 최적화 스케줄러](#4-성능-최적화-스케줄러)
5. [스케줄러 성능 지표](#5-스케줄러-성능-지표)
6. [모니터링 및 운영](#6-모니터링-및-운영)

---

## 1. 스케줄러 개요

Order Service는 **7개의 스케줄러**를 통해 이벤트 발행, 주문 상태 관리, 성능 최적화, 장애 복구를 자동화합니다.

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

---

## 2. Outbox 관련 스케줄러

### 2.1 Outbox Event Publisher Scheduler

**역할**: PENDING 상태의 Outbox 이벤트를 Kafka로 발행

**구현 클래스**: `OutboxEventScheduler`

```java
@Scheduled(fixedDelay = 5000) // 5초마다
@Transactional
public void publishPendingEvents() {
    // 1. PENDING 이벤트 조회 (FOR UPDATE SKIP LOCKED)
    List<OutboxEventEntity> pendingEvents =
        outboxRepository.findPendingEventsForUpdate(100);
    
    // 2. 각 이벤트 Kafka 발행
    for (OutboxEventEntity event : pendingEvents) {
        publishEventWithTransaction(event);
    }
}
```

**설정**
- 실행 주기: 5초
- 배치 크기: 100개
- 동시성 제어: FOR UPDATE SKIP LOCKED
- 타임아웃: 5초

**효과**
- 이벤트 발행 성공률: 99.9%
- 평균 발행 지연: 5초 이내
- 중복 발행: 0건

**상세 내용**: [Outbox 패턴 문서](./OUTBOX_PATTERN.md) 참고

---

### 2.2 Failed Event Retry Scheduler

**역할**: FAILED 상태의 이벤트 재시도

**구현 클래스**: `OutboxEventScheduler`

```java
@Scheduled(fixedDelay = 600000) // 10분마다
public void retryFailedEvents() {
    Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
    
    // 1. 재시도 가능한 FAILED 이벤트 조회
    List<OutboxEventEntity> failedEvents =
        outboxRepository.findFailedEventsForRetry(oneHourAgo, Pageable.ofSize(50));
    
    // 2. 각 이벤트 재시도
    for (OutboxEventEntity event : failedEvents) {
        if (event.canRetry()) {
            retryEventWithTransaction(event);
        }
    }
}
```

**설정**
- 실행 주기: 10분
- 최대 재시도: 3회
- 재시도 간격: 1시간
- 배치 크기: 50개

**재시도 조건**
```sql
WHERE status = 'FAILED'
  AND retry_count < 3
  AND failed_at < :oneHourAgo
```

**효과**
- 자동 복구율: 95% 이상
- 수동 개입 최소화
- 이벤트 유실 방지

---

### 2.3 Outbox Cleanup Scheduler

**역할**: 오래된 PUBLISHED 이벤트 삭제

**구현 클래스**: `OutboxEventScheduler`

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

**설정**
- 실행 주기: 매일 자정
- 보관 기간: 7일
- 삭제 대상: PUBLISHED 상태

**효과**
- DB 용량 관리
- 쿼리 성능 유지
- 인덱스 효율성 향상

---

## 3. 주문 상태 관리 스케줄러

### 3.1 Pending Order Timeout Scheduler

**역할**: 5분 이상 PENDING 상태인 주문 자동 취소

**구현 클래스**: `PendingOrderTimeoutScheduler`

```java
@Scheduled(fixedDelay = 60_000) // 1분마다
public void cancelTimedOutPendingOrders() {
    Instant timeoutThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
    
    // 1. 5분 초과 PENDING 주문 조회
    List<Order> timedOutOrders = orderCommandPort.findTimedOutPendingOrders(
        OrderStatus.PENDING,
        timeoutThreshold,
        PageRequest.of(0, 100)
    );
    
    // 2. 각 주문 취소 처리
    for (Order order : timedOutOrders) {
        CancelOrderCommand command = CancelOrderCommand.ofSystem(
            order.getOrderId(),
            order.getUserId(),
            "결제 미완료로 자동 취소 (5분 타임아웃)"
        );
        
        cancelOrderUseCase.cancelOrder(command);
    }
}
```

**설정**
- 실행 주기: 1분
- 타임아웃: 5분
- 처리 방식: 배치 처리 (100개씩)

**처리 흐름**
```
1. PENDING 주문 조회 (5분 초과)
   ↓
2. 주문 상태 변경 (PENDING → CANCELLED)
   ↓
3. Outbox 이벤트 저장 (order.cancelled)
   ↓
4. Kafka 이벤트 발행 (Outbox Scheduler가 처리)
   ↓
5. 재고 복구 (Stock Service)
   ↓
6. 포인트 환불 (User Service)
```

**효과**
- 평균 취소 처리 시간: 5분 55초
- 재고 복구율: 100%
- 포인트 환불율: 100%

**테스트 결과**
```
취소된 주문: 73건
최소 취소 시간: 5분 54초
평균 취소 시간: 5분 55초
최대 취소 시간: 5분 56초

보상 트랜잭션:
- 재고 복구: 219개 → 400개 (100%)
- 포인트 환불: 73,000원 (100%)
```

---

### 3.2 Auto Confirm Scheduler

**역할**: 배송 완료 후 7일 경과 시 자동 구매 확정

**구현 클래스**: `AutoConfirmScheduler`

```java
@Scheduled(cron = "0 * * * * ?") // 1분마다
protected void executeAutoConfirmBatch() {
    log.info("====== 자동 구매확정 배치 작업 시작 ======");
    
    try {
        // Job parameters 생성 (매번 다른 파라미터로 실행되도록)
        JobParameters params = new JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
        
        // Job 실행
        jobLauncher.run(autoConfirmPurchaseJob, params);
        
        log.info("====== 자동 구매확정 배치 작업 완료 ======");
    } catch (Exception e) {
        log.error("====== 자동 구매확정 배치 작업 실패 ======", e);
    }
}
```

**설정**
- 실행 주기: 1분
- 대기 기간: 배송 완료 후 7일
- 처리 방식: Spring Batch (1000개씩)

**자동 확정 조건**
```
주문 상태: PAID (결제 완료)
경과 시간: paymentCompletedAt + 7일
고객 클레임: 없음
```

**구매 확정 후 처리**
1. **주문 상태 변경**: PAID → PURCHASE_CONFIRMED
2. **포인트 적립**: 주문 금액의 1% 적립 (Kafka 이벤트)
3. **판매자 정산**: 정산 대상으로 등록 (Kafka 이벤트)
4. **리뷰 작성 알림**: 고객에게 리뷰 작성 요청

**효과**
- 자동 정산 프로세스 지원
- 판매자 정산 속도 향상
- 고객 편의성 증대

---

## 4. 성능 최적화 스케줄러

### 4.1 Cache Warming Scheduler

**역할**: 인기 주문 데이터를 L1(Caffeine) + L2(Redis) 2단계 캐시에 미리 적재

**구현 클래스**: `CacheWarmingScheduler`

#### 4.1.1 애플리케이션 시작 시 캐시 워밍

```java
@EventListener(ApplicationReadyEvent.class)
public void warmupCacheOnStartup() {
    // 최근 24시간 이내 주문 ID 조회
    Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    List<UUID> recentOrderIds = orderRepository.findRecentOrderIds(oneDayAgo);
    
    // 각 주문 캐시 적재
    for (UUID orderId : recentOrderIds) {
        orderQueryPort.findOrderDetail(orderId)
            .ifPresent(dto -> {
                orderCachePort.updateOrderCache(orderId, dto);
            });
    }
}
```

**설정**
- 실행 시점: 애플리케이션 시작 시
- 대상: 최근 24시간 내 주문
- 목적: Cold Start 방지 (L1+L2 동시 적재)

#### 4.1.2 Hot Data 재캐싱

```java
@Scheduled(cron = "0 0 */6 * * ?") // 매 6시간 (0시, 6시, 12시, 18시)
public void refreshHotDataCache() {
    // 최근 6시간 이내 주문 ID 조회
    Instant sixHoursAgo = Instant.now().minus(6, ChronoUnit.HOURS);
    List<UUID> hotOrderIds = orderRepository.findRecentOrderIds(sixHoursAgo);
    
    // 각 주문 캐시 갱신
    for (UUID orderId : hotOrderIds) {
        orderQueryPort.findOrderDetail(orderId)
            .ifPresent(dto -> {
                orderCachePort.updateOrderCache(orderId, dto);
            });
    }
}
```

**설정**
- 실행 주기: 6시간마다
- 대상: 최근 6시간 내 주문
- 목적: 이벤트 누락 방어

#### 4.1.3 Cold Data 정리

```java
@Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
public void cleanupColdDataCache() {
    // 7일 이상 지난 주문 ID 조회
    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    List<UUID> oldOrderIds = orderRepository.findOrderIdsBefore(sevenDaysAgo);
    
    // 각 주문 캐시 삭제
    for (UUID orderId : oldOrderIds) {
        orderCachePort.evictOrderCache(orderId);
    }
}
```

**설정**
- 실행 주기: 매일 새벽 2시
- 대상: 7일 이상 경과한 주문
- 목적: 메모리 최적화

**효과**
- Cache Hit Rate: 85~90%
- Cold Start 방지
- 조회 성능 향상: L1 히트 < 1ms, L2 히트 < 10ms (DB 대비 50배)

**상세 내용**: [시스템 아키텍처 문서](./ORDER_ARCHITECTURE.md)의 캐싱 전략 섹션 참고

---

### 4.2 Saga Recovery Scheduler

**역할**: 타임아웃된 Saga 자동 복구

**구현 클래스**: `SagaRecoveryScheduler`

```java
@Scheduled(fixedDelay = 600_000) // 10분마다
public void recoverTimedOutSagas() {
    Instant timeoutThreshold = Instant.now().minus(10, ChronoUnit.MINUTES);
    
    // 타임아웃된 RUNNING 상태 Saga 조회
    List<SagaInstance> timedOutSagas =
        sagaInstancePort.findTimedOutRunningSagas(
            SagaStatus.RUNNING,
            timeoutThreshold,
            SagaStepName.REQUEST_STOCK_RESERVATION
        );
    
    // 각 Saga 보상 트랜잭션 실행
    for (SagaInstance saga : timedOutSagas) {
        sagaRecoveryService.compensateSaga(saga);
        sagaInstancePort.save(saga);
    }
}
```

**설정**
- 실행 주기: 10분
- 타임아웃: 10분
- 대상: REQUEST_STOCK_RESERVATION 단계에서 응답 대기 중인 Saga

**복구 조건**
```
- 상태: RUNNING
- 생성 후 10분 초과
- 마지막 단계: REQUEST_STOCK_RESERVATION (재고 예약 요청 후 응답 대기)
```

**복구 처리**
1. **보상 트랜잭션 실행**: 완료된 Step들에 대해 역순 보상
2. **Saga 상태 변경**: RUNNING → FAILED
3. **메트릭 기록**: 타임아웃 발생 기록

**효과**
- 타임아웃 Saga 자동 복구
- 데이터 정합성 유지
- 수동 개입 최소화

**상세 내용**: [Saga 패턴 문서](./SAGA_PATTERN.md) 참고

---

## 5. 스케줄러 성능 지표

| 스케줄러 | 실행 주기 | 평균 처리 시간 | 처리 성공률 | 배치 크기 |
|----------|-----------|----------------|-------------|-----------|
| Outbox Publisher | 5초 | 200ms | 99.9% | 100개 |
| Failed Event Retry | 10분 | 500ms | 95% | 50개 |
| Outbox Cleanup | 매일 자정 | 2초 | 100% | - |
| Pending Timeout | 1분 | 1.5초 | 100% | 100개 |
| Cache Warming (Startup) | 시작 시 | 3초 | 100% | - |
| Cache Warming (Hot Data) | 6시간마다 | 3초 | 100% | - |
| Cache Cleanup | 매일 02:00 | 2초 | 100% | - |
| Auto Confirm | 1분 | 5초 | 100% | 1000개 |
| Saga Recovery | 10분 | 1초 | 100% | - |

### 성능 최적화 포인트

1. **배치 처리**: 한 번에 여러 건 처리로 오버헤드 감소
2. **FOR UPDATE SKIP LOCKED**: 동시성 제어로 중복 처리 방지
3. **인덱스 활용**: 상태별 인덱스로 조회 성능 향상
4. **트랜잭션 최적화**: 필요한 범위만 트랜잭션 처리

---

## 6. 모니터링 및 운영

### 주요 모니터링 지표

#### 1. 스케줄러 실행 상태

```sql
-- 최근 1시간 스케줄러 실행 로그 확인
SELECT 
    scheduler_name,
    execution_time,
    success_count,
    failure_count
FROM scheduler_execution_log
WHERE executed_at >= NOW() - INTERVAL '1 hour'
ORDER BY executed_at DESC;
```

#### 2. Outbox 이벤트 발행 현황

```sql
-- PENDING 이벤트 수
SELECT COUNT(*) FROM p_outbox_event WHERE status = 'PENDING';

-- FAILED 이벤트 수
SELECT COUNT(*) FROM p_outbox_event WHERE status = 'FAILED';

-- 평균 발행 지연 시간
SELECT 
    AVG(EXTRACT(EPOCH FROM (published_at - created_at))) as avg_delay_seconds
FROM p_outbox_event
WHERE status = 'PUBLISHED'
  AND created_at >= NOW() - INTERVAL '1 hour';
```

#### 3. 주문 자동 취소 현황

```sql
-- 최근 1시간 자동 취소된 주문 수
SELECT COUNT(*) 
FROM p_order 
WHERE status = 'CANCELLED'
  AND cancelled_at >= NOW() - INTERVAL '1 hour'
  AND cancelled_reason LIKE '%자동 취소%';
```

### Grafana 대시보드

**주요 패널**
1. **스케줄러 실행 횟수** (Counter)
2. **평균 처리 시간** (Gauge)
3. **처리 성공률** (Gauge)
4. **PENDING 이벤트 수** (Single Stat)
5. **FAILED 이벤트 수** (Single Stat)
6. **자동 취소 주문 수** (Counter)

### 알림 설정

**Slack 알림 조건**
1. Outbox 이벤트 발행 실패율 > 5%
2. FAILED 이벤트 > 100건
3. 평균 발행 지연 > 60초
4. 재시도 3회 초과 이벤트 발생
5. 스케줄러 실행 실패

### 트러블슈팅

#### 문제 1: Outbox 이벤트 발행 지연

**증상**: PENDING 이벤트가 5초 이상 지연

**원인**
- Kafka 브로커 장애
- 네트워크 지연
- 스케줄러 인스턴스 부족

**해결**
1. Kafka 브로커 상태 확인
2. 네트워크 연결 확인
3. 스케줄러 인스턴스 수 증가

#### 문제 2: 자동 취소가 실행되지 않음

**증상**: 5분 이상 경과한 PENDING 주문이 취소되지 않음

**원인**
- 스케줄러 미실행
- 트랜잭션 롤백
- 주문 조회 쿼리 오류

**해결**
1. 스케줄러 실행 로그 확인
2. 트랜잭션 롤백 원인 확인
3. 주문 조회 쿼리 성능 확인

#### 문제 3: 캐시 워밍 효과 없음

**증상**: Cache Hit Rate가 85% 미만

**원인**
- L1 TTL(5분) 또는 L2 TTL(1시간) 설정 문제
- 인기 주문 ID 수집 로직 오류
- Redis 메모리 부족 (L2 적재 실패)
- Caffeine max size(500) 초과로 L1 eviction 발생

**해결**
1. L1 TTL(Caffeine, 5분) / L2 TTL(Redis, 1시간) 확인
2. 인기 주문 수집 로직 검증
3. Redis 메모리 사용량 확인
4. Caffeine `recordStats()` 메트릭으로 L1 히트율 확인

---

## 🎯 핵심 요약

✅ **7개 스케줄러로 자동화**: 이벤트 발행, 주문 관리, 성능 최적화, 장애 복구  
✅ **99.9% 이벤트 발행 성공률**: Outbox 패턴 + 자동 재시도  
✅ **100% 자동 취소 성공률**: 재고/포인트 복구 완벽 처리  
✅ **성능 최적화**: 캐시 워밍으로 조회 성능 50배 향상  
✅ **모니터링 및 알림**: 실시간 장애 감지 및 대응

---

**작성일**: 2026-01-12  
**작성자:** 차초희  
**검토자:** 차초희  
**최종 수정일:** 2026-04-09  
**버전**: 2.0 (Caffeine L1+Redis L2 2단계 캐시 워밍 반영)
