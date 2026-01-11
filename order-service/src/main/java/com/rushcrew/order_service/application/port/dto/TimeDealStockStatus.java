package com.rushcrew.order_service.application.port.dto;

public enum TimeDealStockStatus {
	AVAILABLE,
	RESERVED,
	SOLD,
	PAUSED;

	public static TimeDealStockStatus from(String status) {
		try {
			return TimeDealStockStatus.valueOf(status);
		} catch (Exception e) {
			return PAUSED;	// 알 수 없는 상태는 재고 정지로 처리
		}
	}
}
