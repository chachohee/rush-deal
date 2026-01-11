# 📐 Saga 패턴 상세 문서

> RushDeal Order Service의 Orchestration 방식 Saga 패턴 구현

---

## 목차

1. [Saga 패턴이란?](#1-saga-패턴이란)
2. [Orchestration 방식](#2-orchestration-방식)
3. [RushDeal의 Saga 구현](#3-rushdeal의-saga-구현)
4. [단계별 동작 흐름](#4-단계별-동작-흐름)
5. [보상 트랜잭션](#5-보상-트랜잭션)
6. [코드 구현](#6-코드-구현)

---

## 1. Saga 패턴이란?

### 정의

Saga 패턴은 **분산 환경에서 데이터 일관성을 유지하기 위한 트랜잭션 관리 패턴**입니다.

하나의 비즈니스 트랜잭션을 여러 개의 작은 로컬 트랜잭션으로 분할하고, 각 트랜잭션이 순차적으로 실행됩니다. 만약 중간에 실패가 발생하면 **보상 트랜잭션(Compensating Transaction)**을 통해 이전 단계들을 되돌립니다.

### 핵심 개념

```
Saga = T1 → T2 → T3 → ... → Tn

실패 시:
Saga = T1 → T2 → (T3 실패) → C2 → C1
```

- **Ti**: i번째 로컬 트랜잭션
- **Ci**: Ti를 취소하는 보상 트랜잭션

### ACID vs Saga

| 속성 | ACID (전통적 방식) | Saga 패턴 |
|------|-------------------|----------|
| **Atomicity (원자성)** | 전체 성공 또는 전체 실패 | 로컬 트랜잭션은 개별 커밋 |
| **Consistency (일관성)** | 즉시 일관성 보장 | 최종 일관성 보장 (Eventually Consistent) |
| **Isolation (격리성)** | 완전한 격리 | 부분적 격리 (Dirty Read 가능) |
| **Durability (지속성)** | ✅ 보장 | ✅ 보장 |

---

## 2. Orchestration 방식

### 개념

중앙 **Orchestrator**가 전체 흐름을 제어하는 방식입니다.

```
[Client] → [Orchestrator] → [Step 1] → [Step 2] → [Step 3]
```

### RushDeal이 Orchestration을 선택한 이유

1. **명확한 비즈니스 흐름**: 주문 → 포인트 → 재고 순서가 명확
2. **복잡한 검증 로직**: 재고 검증, 구매 제한 체크 등 다양한 검증 필요
3. **디버깅 용이성**: 실패 지점 추적 및 분석 용이
4. **요구사항 변경 대응**: 중앙에서 흐름 수정 가능

---

## 3. RushDeal의 Saga 구현

### 전체 아키텍처

```
[Client]
   ↓ HTTP Request
[OrderCreationSagaOrchestrator]
   ├─→ Step 1: ValidateStock (동기)
   ├─→ Step 2: UsePoint (동기, FeignClient)
   ├─→ Step 3: RequestStockReservation (비동기, Outbox)
   └─→ Response (sagaId, PROCESSING)
         ↓
   [OutboxEventScheduler] (5초마다)
         ↓
   [Kafka: stock.reservation.requested]
         ↓
   [Stock Service]
         ↓ 재고 예약
   [Kafka: stock.reserved]
         ↓
   [StockReservationEventListener]
         ↓
   [CreateOrderStep]
         ↓
   [Order 생성 완료]
```

### Saga 단계 정의

| 순서 | 단계 | 타입 | 동작 | 보상 |
|------|------|------|------|------|
| 1 | ValidateStock | 검증 | 재고/구매제한 체크 | 없음 |
| 2 | UsePoint | Compensable | 포인트 차감 | 포인트 환불 |
| 3 | RequestStockReservation | Compensable | 재고 예약 요청 | 재고 예약 취소 |
| 4 | CreateOrder | Retryable | 주문 생성 | 주문 취소 |

**트랜잭션 유형**
- **Compensable**: 실패 시 보상 가능
- **Retryable**: 실패 시 재시도 가능

---

## 4. 단계별 동작 흐름

### Phase 1: 동기 처리 (즉시 응답)

#### Step 1: ValidateStock

```java
// ValidateStockStep.java
public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
    // 1. 큐 토큰 검증
    queueTokenValidator.validate(...);
    
    // 2. 타임딜 조회 및 검증
    TimeDealInfo timeDeal = timeDealStockPort.getTimeDeal(...);
    timeDealValidator.validate(timeDeal);
    
    // 3. 주문 아이템 검증
    orderItemValidator.validate(...);
    
    // 4. 구매 제한 검증 (5개/인)
    purchaseLimitValidator.validate(...);
    
    return SagaStepResult.success();
}
```

**특징**
- 읽기 전용 작업 (상태 변경 X)
- 보상 트랜잭션 불필요
- 빠른 실패 (Fast Fail)

#### Step 2: UsePoint

```java
// UsePointStep.java
public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
    Long pointUsed = data.getCommand().pointUsed();
    
    if (pointUsed == null || pointUsed <= 0) {
        return SagaStepResult.success(); // 스킵
    }
    
    // FeignClient 동기 호출
    pointPort.usePoint(userId, orderId, pointUsed, sagaId);
    
    return SagaStepResult.success();
}
```

**특징**
- **동기 처리** (FeignClient)
- **USE_PENDING** 상태로 포인트 차감
- 실패 시 보상 트랜잭션 필요

#### Step 3: RequestStockReservation

```java
// RequestStockReservationStep.java
public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
    // Outbox 이벤트 저장
    outboxPort.createAndSave(
        "ORDER_SAGA",
        context.getSagaId(),
        OutboxEventType.STOCK_RESERVATION_REQUESTED,
        payload
    );
    
    // ⚡ 여기서 클라이언트에 즉시 응답 반환
    return SagaStepResult.success();
}
```

**특징**
- **Outbox 패턴** 사용으로 이벤트 발행 신뢰성 보장
- DB 트랜잭션과 함께 커밋
- 실제 Kafka 발행은 스케줄러가 담당

**응답**
```json
{
  "sagaId": "uuid",
  "orderId": "uuid",
  "status": "PROCESSING"
}
```

평균 응답시간: **2.06초** ⚡

---

### Phase 2: 비동기 처리 (Kafka 이벤트)

#### Step 4: CreateOrder (재고 예약 완료 후)

```java
// CreateOrderStep.java
@Transactional
public void execute(SagaContext context, OrderCreationSagaData data, StockReservedEvent event) {
    // 1. OrderItem 생성
    var orderItems = event.reservedItems().stream()
        .map(reservedItem -> OrderItem.create(...))
        .toList();
    
    // 2. Order 생성
    Order order = Order.create(
        data.getOrderId(),
        command.userId(),
        orderItems,
        command.pointUsed(),
        shippingInfo
    );
    
    // 3. Order 저장
    Order savedOrder = orderCommandPort.save(order);
    
    // 4. ORDER_CREATED 이벤트 발행 (Outbox)
    outboxPort.createAndSave(
        "ORDER",
        savedOrder.getOrderId(),
        OutboxEventType.ORDER_CREATED,
        payload
    );
}
```

**특징**
- **Retryable Transaction**: 실패 시 재시도 가능
- 멱등성 보장 (Order ID로 중복 방지)

---

## 5. 보상 트랜잭션

### 보상 트랜잭션이 필요한 경우

#### 1. 구매 제한 초과 (Step 1 실패)

```
Step 1: ValidateStock ❌ (6개 > 5개)
→ 보상: 없음 (포인트 차감 전이므로)
```

**결과**: HTTP 400 BadRequest 즉시 반환

#### 2. 재고 예약 실패 (Step 3 이후 실패)

```
Step 1: ValidateStock ✅
Step 2: UsePoint ✅ (1,000원 차감)
Step 3: RequestStockReservation ✅ (이벤트 발행)
→ 재고 예약 실패 (재고 부족 or 시스템 오류)

→ 보상 트랜잭션 실행
```

**보상 흐름**
```java
// OrderCreationSagaOrchestrator.java
private void compensateCompletedSteps(SagaInstance saga, SagaContext context, OrderCreationSagaData data) {
    // USE_POINT가 완료되었다면 보상
    if (saga.hasCompletedStep(SagaStepName.USE_POINT)) {
        usePointStep.compensate(context, data);
    }
}
```

#### 3. UsePointStep 보상

```java
// UsePointStep.java
public void compensate(SagaContext context, OrderCreationSagaData data) {
    Long pointUsed = data.getCommand().pointUsed();
    
    if (pointUsed == null || pointUsed <= 0) {
        return; // 스킵
    }
    
    // 포인트 사용 취소 이벤트 발행
    pointEventPort.publishPointUseCancellRequested(
        userId,
        orderId,
        sagaId,
        pointUsed,
        "Saga 보상으로 인한 포인트 사용 취소"
    );
}
```

#### 4. 주문 자동 취소 (5분 타임아웃)

```
Step 1~4: 모두 성공 ✅
주문 상태: PENDING
5분 경과 → PendingOrderTimeoutScheduler 동작

→ 보상 트랜잭션 실행
```

**보상 흐름**
```java
// PendingOrderTimeoutScheduler.java
@Scheduled(fixedDelay = 60_000) // 1분마다
public void cancelTimedOutPendingOrders() {
    Instant timeoutThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
    
    List<Order> timedOutOrders = orderCommandPort.findTimedOutPendingOrders(
        OrderStatus.PENDING,
        timeoutThreshold,
        PageRequest.of(0, 100)
    );
    
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

**보상 작업**
1. **재고 복구**: Stock Service가 order.cancelled 이벤트 수신
2. **포인트 환불**: User Service가 order.cancelled 이벤트 수신

---

## 6. 코드 구현

### OrderCreationSagaOrchestrator (핵심 코드)

```java
@Component
@RequiredArgsConstructor
public class OrderCreationSagaOrchestrator {
    
    private final ValidateStockStep validateStockStep;
    private final UsePointStep usePointStep;
    private final RequestStockReservationStep requestStockReservationStep;
    private final SagaInstancePort sagaInstancePort;
    
    @Transactional
    public UUID execute(CreateOrderCommand command) {
        // 1. Saga 생성
        SagaInstance saga = SagaInstance.create("CREATE_ORDER", command.userId());
        sagaInstancePort.save(saga);
        
        // 2. Context / Data 구성
        SagaContext context = SagaContext.builder()
            .sagaId(saga.getSagaId())
            .userId(command.userId())
            .build();
        
        OrderCreationSagaData data = OrderCreationSagaData.builder()
            .command(command)
            .orderId(UUID.randomUUID())
            .build();
        
        try {
            // Step 1: ValidateStock
            SagaStepResult validateResult = validateStockStep.execute(context, data);
            handleStepResult(saga, validateResult);
            saga.addStep(SagaStepName.VALIDATE_STOCK, SagaStatus.COMPLETED);
            
            // Step 2: UsePoint
            SagaStepResult pointResult = usePointStep.execute(context, data);
            handleStepResult(saga, pointResult);
            saga.addStep(SagaStepName.USE_POINT, SagaStatus.COMPLETED);
            
            // Step 3: RequestStockReservation
            SagaStepResult stockReservationResult = requestStockReservationStep.execute(context, data);
            handleStepResult(saga, stockReservationResult);
            saga.addStep(SagaStepName.REQUEST_STOCK_RESERVATION, SagaStatus.WAITING);
            
            // SagaData 저장
            saga.saveData(data);
            sagaInstancePort.save(saga);
            
            return saga.getSagaId();
            
        } catch (Exception e) {
            // 보상 트랜잭션 실행
            compensateCompletedSteps(saga, context, data);
            throw e;
        }
    }
    
    private void compensateCompletedSteps(SagaInstance saga, SagaContext context, OrderCreationSagaData data) {
        // USE_POINT가 완료되었다면 보상
        if (saga.hasCompletedStep(SagaStepName.USE_POINT)) {
            usePointStep.compensate(context, data);
        }
    }
}
```

### SagaInstance (도메인 모델)

```java
@Entity
@Table(name = "p_saga_instance", schema = "order_schema")
public class SagaInstance {
    
    @Id
    private UUID sagaId;
    
    private String sagaType;
    private Long userId;
    private SagaStatus status;
    
    @OneToMany(mappedBy = "sagaInstance", cascade = CascadeType.ALL)
    private List<SagaStep> steps;
    
    @Column(columnDefinition = "TEXT")
    private String sagaData; // JSON 형태로 저장
    
    public boolean hasCompletedStep(SagaStepName sagaStepName) {
        return this.steps.stream()
            .anyMatch(step ->
                step.getStepName().equals(sagaStepName.name())
                    && step.getStatus() == SagaStatus.COMPLETED
            );
    }
}
```

---

## 🎯 Saga 패턴 핵심 원칙

✅ **최종 일관성**: 분산 환경에서 최종적으로 일관된 상태 보장  
✅ **자동 보상**: 실패 시 자동으로 이전 상태로 복구  
✅ **느슨한 결합**: 비동기 이벤트 기반 통신  
✅ **장애 격리**: 한 서비스 실패가 전체 시스템에 영향 최소화

---

**작성일**: 2026-01-12  
**작성자**: 차초희  
**버전**: 2.0
