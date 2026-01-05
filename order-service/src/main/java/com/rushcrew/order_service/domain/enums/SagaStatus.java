package com.rushcrew.order_service.domain.enums;

public enum SagaStatus {
	RUNNING,        // 실행 중
	WAITING,		// 외부 응답 대기 (비동기)
	COMPLETED,      // 완료
	FAILED,         // 실패
	COMPENSATING,   // 보상 트랜잭션 실행 중
	COMPENSATED     // 보상 완료
}
