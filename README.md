# 🚀 담당 역할: Order Service (주문 서비스)

> Saga 패턴과 Outbox 패턴 기반의 고신뢰성 분산 주문 처리 서비스

## 🎯 주요 특징

- **Saga 패턴 (Orchestration)**: 분산 트랜잭션 조율 및 자동 보상
- **Outbox 패턴**: 이벤트 발행 성공률 99.9% 달성
- **CQRS + 2-Tier 캐싱**: 조회 성능 50배 향상 (500ms → 10ms)
- **동시성 제어**: FOR UPDATE SKIP LOCKED로 중복 발행 방지
- **자동 취소**: 5분 타임아웃 주문 자동 취소 및 재고/포인트 복구

## 📊 성능 검증 (부하 테스트)

### 대규모 동시 접속 테스트

| 테스트 규모 | 성공률 | P95 응답시간 | 처리량 | 데이터 정합성 |
|------------|--------|-------------|--------|--------------|
| **100명** | 73% | 2.51초 | 38.9 RPS | 100% ✅ |
| **1000명** | 75.30% | 6.01초 | 154.19 RPS | 100% ✅ |

### 핵심 검증 결과

- ✅ **1000명 동시 주문 처리**: 753건 성공 (75.30%)
- ✅ **재고 정합성**: 2,308개 예약 → 100% 정확 복구
- ✅ **포인트 정합성**: 753,000원 차감 → 100% 정확 환불
- ✅ **자동 취소**: 753건 PENDING → CANCELLED (100%)
- ✅ **큐 시스템**: 1000명 토큰 발급 100% 성공 (P95 1.38초)

### 스케일업 인사이트

- **처리량 4배 향상**: 100명(38.9 RPS) → 1000명(154.19 RPS)
- **성공률 향상**: 73% → 75.30% (+2.3%p)
- **데이터 정합성 유지**: 10배 규모에서도 100% 정합성 보장
- **개선 과제**: P95 응답시간 최적화 (6.01초 → 목표 5초 이하)

## 📚 상세 문서

**→ [Order Service README](./order-service/README.md)** - 전체 개요 및 API 명세

### 아키텍처 및 패턴 문서
- [시스템 아키텍처](./order-service/docs/md/ORDER_ARCHITECTURE.md) - 전체 시스템 구조
- [Saga 패턴](./order-service/docs/md/SAGA_PATTERN.md) - 분산 트랜잭션 관리
- [Outbox 패턴](./order-service/docs/md/OUTBOX_PATTERN.md) - 이벤트 발행 신뢰성

### 성능 테스트 문서
- [테스트 실행 가이드](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_GUIDE.md) - 단계별 테스트 방법 (1000명)
- [테스트 결과 요약](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_SUMMARY.md) - 핵심 성과 지표 (100명/1000명 비교)
- [테스트 결과 상세](./order-service/docs/md/ORDER_FLOW_VALIDATION_TEST_RESULT.md) - 상세 검증 결과

### 테스트 스크립트
- [k6 부하 테스트 스크립트](./order-service/k6/tests) - 큐 토큰 발급 및 주문 생성 테스트
- [자동화 검증 스크립트](./order-service/k6/scripts) - 전체 플로우 자동 실행 및 검증

## 🎥 시연 영상

- **100명 동시 주문 테스트**: [YouTube](https://youtu.be/zYQMJPPH7uQ)
- **1000명 동시 주문 테스트**: [YouTube](https://youtu.be/54cJzqk-PPM)

---

**→ [Rush Deal 프로젝트 전체 README](RUSHDEAL_README.md)**

---

**작성일**: 2026-01-12  
**작성자:** 차초희  
**검토자:** 차초희  
**최종 수정일:** 2026-01-16  
**버전:** 2.0 (1000명 부하 테스트 반영)
