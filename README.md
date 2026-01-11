# 담당 서비스: 주문 서비스 (Order Service)
> 주문 서비스의 전체 플로우, Saga 패턴 구현, 동시성 제어 등 상세 내용은 아래 문서를 참고하세요.

**→ [Order Service 상세 문서](./order-service/README.md)**

주요 내용:
- Saga 패턴 기반 분산 트랜잭션 관리
- Outbox 패턴을 통한 이벤트 발행 신뢰성 보장
- CQRS 패턴 적용 및 2-Tier 캐싱 전략
- 100명 동시 주문 테스트 결과 (100% 성공률, P95 응답시간 2.5초)
- 자동 취소 스케줄러 및 재고 복구 메커니즘

**→ [Rush Deal 프로젝트 README](RUSHDEAL_README.md)**
