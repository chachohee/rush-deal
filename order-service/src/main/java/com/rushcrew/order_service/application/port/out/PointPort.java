package com.rushcrew.order_service.application.port.out;

import java.util.UUID;


public interface PointPort {
	/** 포인트 사용 */
	void usePoint(Long userId, String tempOrderId, Long pointUsed, UUID sagaId);
	/** 포인트 사용 취소 */
	void cancelPointUse(Long userId, String tempOrderId, UUID sagaId);
}
