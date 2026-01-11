package com.rushcrew.order_service.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public interface PointEventPort {
	/** 포인트 적립 요청 이벤트 발행 */
	void publishPointEarnRequested(Long userId, UUID orderId, BigDecimal finalAmount, UUID sagaId, String reason);
	/** 포인트 사용 취소 요청 이벤트 발행 */
	void publishPointUseCancellRequested(Long userId, UUID orderId, UUID sagaId, Long pointUsed, String reason);
	/** 포인트 환불 요청 이벤트 발행 */
	void publishPointRefundRequested(Long aLong, UUID orderId, UUID sagaId, Long pointUsed, String reason);
}
