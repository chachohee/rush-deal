package com.rushcrew.order_service.application.port.out;

import java.util.UUID;

public interface QueueEventPort {
	/** 주문 생성이 완료되면 대기열 토큰 삭제 이벤트 발행 */
	void publishTokenRemoveEvent(Long userId, UUID productId, String token);
}
