# 🏗 Order Service 시스템 아키텍처

> 대규모 동시 주문 처리를 위한 MSA 기반 아키텍처

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [레이어 구조](#2-레이어-구조)
3. [주문 생성 플로우](#3-주문-생성-플로우)
4. [Saga 패턴](#4-saga-패턴)
5. [Outbox 패턴](#5-outbox-패턴)
6. [동시성 제어](#6-동시성-제어)

---

## 1. 아키텍처 개요

### 전체 시스템 구성

```
┌──────────────────────────────────────────────────────────────┐
│                     API Gateway Layer                        │
│  - Spring Cloud Gateway                                      │
│  - 인증/인가 (JWT)                                           │
└──────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────┐
│                      Order Service                           │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Presentation Layer                               │     │
│  │  - OrderCommandController                         │     │
│  │  - OrderQueryController                           │     │
│  └────────────────────────────────────────────────────┘     │
│                              ↓                               │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Application Layer                                │     │
│  │  - OrderCreationSagaOrchestrator                  │     │
│  │  - CreateOrderUseCase                             │     │
│  │  - OrderQueryService                              │     │
│  └────────────────────────────────────────────────────┘     │
│                              ↓                               │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Domain Layer                                      │     │
│  │  - Order (Aggregate Root)                         │     │
│  │  - OrderItem                                       │     │
│  │  - SagaInstance                                    │     │
│  └────────────────────────────────────────────────────┘     │
│                              ↓                               │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Infrastructure Layer                              │     │
│  │  - OrderRepository (PostgreSQL)                   │     │
│  │  - OutboxEventRepository                          │     │
│  │  - Kafka (Event Bus)                               │     │
│  │  - Feign Client (User Service)                     │     │
│  └────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────┘
```

### 핵심 컴포넌트

| 컴포넌트 | 역할 | 기술 스택 |
|----------|------|-----------|
| **Controller Layer** | HTTP 요청/응답 처리 | Spring MVC |
| **Saga Orchestrator** | 분산 트랜잭션 조율 | Saga Pattern |
| **Outbox Publisher** | 이벤트 발행 관리 | Outbox Pattern |
| **Repository Layer** | 데이터 영속화 | Spring Data JPA |
| **Event Listener** | Kafka 이벤트 수신 | Spring Kafka |
| **Scheduler** | 주기적 작업 실행 | Spring @Scheduled |

---

## 2. 레이어 구조

### Clean Architecture + DDD

```
┌─────────────────────────────────────────────────────────┐
│                  Presentation Layer                      │
│  - OrderCommandController                               │
│  - OrderQueryController                                  │
│  - DTO 변환                                             │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                      │
│  - OrderCreationSagaOrchestrator (Saga 조율)            │
│  - CreateOrderUseCase (Command CQRS)                     │
│  - OrderQueryService (Query CQRS)                        │
│  - Saga Steps (ValidateStock, UsePoint, etc.)           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                    Domain Layer                         │
│  - Aggregate Root: Order                               │
│  - Entity: OrderItem, OrderReservation                  │
│  - Entity: SagaInstance                                │
│  - Domain Service                                        │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                 Infrastructure Layer                   │
│  - @Repository (JPA)                                    │
│  - FeignClient (User Service)                           │
│  - KafkaTemplate (Event Publishing)                    │
│  - @Scheduled (Scheduler)                              │
└─────────────────────────────────────────────────────────┘
```

### 의존성 규칙

```
Presentation → Application → Domain ← Infrastructure
                                ↑
                                │
                        (인터페이스 의존)
```

**핵심 원칙**
- Domain Layer는 외부 의존성 없음 (순수 비즈니스 로직)
- Infrastructure Layer만 외부 기술 의존
- 의존성 역전 원칙(DIP) 적용

---

## 3. 주문 생성 플로우

### 전체 플로우

```
[Client Request]
      ↓
[OrderCommandController]
      ↓
[OrderCreationSagaOrchestrator]
      ↓
┌─────────────────────────────────────┐
│  Step 1: ValidateStock             │
│  - 큐 토큰 검증                     │
│  - 타임딜 검증                      │
│  - 주문 아이템 검증                  │
│  - 구매 제한 검증 (5개/인)          │
└─────────────────────────────────────┘
      ↓
┌─────────────────────────────────────┐
│  Step 2: UsePoint                  │
│  - 포인트 차감 (FeignClient 동기)   │
│  - USE_PENDING 상태로 차감          │
└─────────────────────────────────────┘
      ↓
┌─────────────────────────────────────┐
│  Step 3: RequestStockReservation  │
│  - OutboxEvent 저장 (PENDING)       │
│  - [DB Transaction Commit]         │
│  - Response to Client (sagaId)      │
└─────────────────────────────────────┘
      ↓
[OutboxEventScheduler] (5초마다)
      ↓
[Kafka: stock.reservation.requested]
      ↓
[Stock Service]
      ↓
[Kafka: stock.reserved]
      ↓
[StockReservationEventListener]
      ↓
[CreateOrderStep]
      ↓
[Order 생성 완료]
```

### 응답 시간

- **평균 응답시간**: 2.06초
- **P95 응답시간**: 2.51초
- **처리량**: 38.5 req/s

---

## 4. Saga 패턴

### Orchestration 방식

**OrderCreationSagaOrchestrator**가 전체 흐름을 중앙에서 제어합니다.

### Saga 단계

| 순서 | 단계 | 타입 | 동작 | 보상 |
|------|------|------|------|------|
| 1 | ValidateStock | 검증 | 재고/구매제한 체크 | 없음 |
| 2 | UsePoint | Compensable | 포인트 차감 | 포인트 환불 |
| 3 | RequestStockReservation | Compensable | 재고 예약 요청 | 재고 예약 취소 |
| 4 | CreateOrder | Retryable | 주문 생성 | 주문 취소 |

### 보상 트랜잭션

실패 시 완료된 Step들에 대해 역순으로 보상:

```java
// UsePointStep 실패 시
if (saga.hasCompletedStep(USE_POINT)) {
    usePointStep.compensate(context, data);
}
```

---

## 5. Outbox 패턴

### 이벤트 발행 프로세스

```
[트랜잭션 내]
      ↓
[OutboxEvent 저장 (PENDING)]
      ↓
[DB Transaction Commit]
      ↓
[OutboxEventScheduler] (5초마다)
      ↓
[FOR UPDATE SKIP LOCKED]
      ↓
[Kafka 발행]
      ↓
[상태 업데이트 (PUBLISHED)]
```

### 동시성 제어

```sql
SELECT * FROM p_outbox_event
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED
```

**다중 인스턴스 환경**
- Instance 1: Row 1~100 락 획득 ✅
- Instance 2: Row 101~200 락 획득 ✅ (1~100은 SKIP)
- → 중복 처리 0건

### 재시도 메커니즘

- **실패 이벤트**: 10분마다 재시도
- **최대 재시도**: 3회
- **7일 이상 PUBLISHED 이벤트**: 자동 삭제

---

## 6. 동시성 제어

### Outbox 동시성 제어

**FOR UPDATE SKIP LOCKED** 사용으로 다중 인스턴스 환경에서도 안전하게 처리

### 주문 자동 취소

**PendingOrderTimeoutScheduler**가 1분마다 실행:
- PENDING 상태 5분 이상 경과 주문 자동 취소
- 재고 복구 및 포인트 환불 처리

### 재고 동시성 제어

Stock Service에서 처리:
- 낙관적 락 (Optimistic Lock) 사용
- 실패 시 비관적 락 (Pessimistic Lock) 자동 전환

---

## 🎯 아키텍처 핵심 원칙

✅ **확장성**: 수평 확장 가능한 Stateless 설계  
✅ **가용성**: Circuit Breaker 및 재시도 전략  
✅ **일관성**: Saga + Outbox 패턴으로 최종 일관성 보장  
✅ **성능**: 비동기 처리로 빠른 응답 시간  
✅ **관찰성**: Prometheus + Grafana 모니터링  
✅ **복원력**: 장애 격리 및 자동 복구

---

**작성일**: 2026-01-12  
**작성자**: 차초희  
**버전**: 2.0
