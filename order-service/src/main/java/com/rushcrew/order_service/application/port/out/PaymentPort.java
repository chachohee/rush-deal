package com.rushcrew.order_service.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentPort {
	/** 결제 요청 */
	boolean requestPayment(UUID orderId, Long userId, BigDecimal finalAmount);
	/** 결제 취소 (주문 환불 시) */
	void cancelPayment(UUID orderId, Long userId);
}
