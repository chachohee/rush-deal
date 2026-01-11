# 📦 Order Service (주문 서비스)

> Saga 패턴과 Outbox 패턴 기반의 고신뢰성 분산 주문 처리 서비스

## 🎯 주요 특징

- **Saga 패턴 (Orchestration)**: 분산 트랜잭션 조율 및 자동 보상
- **Outbox 패턴**: 이벤트 발행 성공률 99.9% 달성
- **CQRS + 2-Tier 캐싱**: 조회 성능 50배 향상 (500ms → 10ms)
- **동시성 제어**: FOR UPDATE SKIP LOCKED로 중복 발행 방지
- **자동 취소**: 5분 타임아웃 주문 자동 취소 및 재고/포인트 복구

## 📊 성능 지표

- **100명 동시 주문 처리**: P95 응답시간 2.51초
- **이벤트 발행 성공률**: 99.9%
- **재고 복구율**: 100%
- **포인트 환불 정확도**: 100%

## 📚 상세 문서

**→ [Order Service README](./order-service/README.md)** - 전체 개요 및 API 명세

**아키텍처 및 패턴 문서:**
- [시스템 아키텍처](./order-service/docs/md/ORDER_ARCHITECTURE.md) - 전체 시스템 구조
- [Saga 패턴](./order-service/docs/md/SAGA_PATTERN.md) - 분산 트랜잭션 관리
- [Outbox 패턴](./order-service/docs/md/OUTBOX_PATTERN.md) - 이벤트 발행 신뢰성

**테스트 문서:**
- [테스트 실행 가이드](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_GUIDE.md) - 단계별 테스트 방법
- [테스트 결과 요약](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_SUMMARY.md) - 핵심 성과 지표
- [테스트 결과 상세](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_RESULT.md) - 상세 검증 결과

---

**→ [Rush Deal 프로젝트 전체 README](RUSHDEAL_README.md)**
