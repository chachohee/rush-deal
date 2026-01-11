package com.rushcrew.order_service.infrastructure.messaging.event;

/**
 * Outbox 이벤트 타입 상수 클래스
 * 
 * 이 클래스는 Outbox 패턴을 통해 Kafka로 발행되는 이벤트 타입을 정의한다
 * OrderEventType과는 목적이 다름
 *
 * OrderEventType: 도메인 이벤트 (OrderHistory 기록용)
 * OutboxEventType: 인프라 이벤트 (외부 서비스 통신용)
 *
 * 예시:
 * 주문 취소 시:
 * OrderHistory: OrderEventType.CANCELLED_BEFORE_PAYMENT
 * Outbox: OutboxEventType.ORDER_CANCELLED
 */
public class OutboxEventType {

	private OutboxEventType() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	// ============================================
	//              주문 이벤트
	// ============================================

	/** 주문 생성 */
	public static final String ORDER_CREATED = "ORDER_CREATED";

	/** 주문 수정 */
	public static final String ORDER_UPDATED = "ORDER_UPDATED";

	/** 주문 취소 (결제 전) */
	public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

	/** 주문 결제 완료 */
	public static final String ORDER_PAID = "ORDER_PAID";

	/** 주문 구매확정 */
	public static final String ORDER_PURCHASE_CONFIRMED = "ORDER_PURCHASE_CONFIRMED";

	/** 주문 환불 (결제 후) */
	public static final String ORDER_REFUNDED = "ORDER_REFUNDED";

	// ============================================
	//              결제 이벤트
	// ============================================

	/** 결제 완료 */
	public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

	/** 결제 취소 */
	public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";

	/** 환불 요청 */
	public static final String REFUND_REQUESTED = "REFUND_REQUESTED";

	// ============================================
	//              포인트 이벤트
	// ============================================

	/** 포인트 적립 요청 */
	public static final String POINT_EARN_REQUESTED = "POINT_EARN_REQUESTED";

	/** 포인트 환불 요청 */
	public static final String POINT_REFUND_REQUESTED = "POINT_REFUND_REQUESTED";

	/** 포인트 사용 취소 요청 */
	public static final String POINT_USE_CANCEL_REQUESTED = "POINT_USE_CANCEL_REQUESTED";

	// ============================================
	//              재고 이벤트
	// ============================================

	/** 재고 예약 요청 */
	public static final String STOCK_RESERVATION_REQUESTED = "STOCK_RESERVATION_REQUESTED";

	/** 재고 복구 요청 */
	public static final String STOCK_ROLLBACK_REQUESTED = "STOCK_ROLLBACK_REQUESTED";
}
