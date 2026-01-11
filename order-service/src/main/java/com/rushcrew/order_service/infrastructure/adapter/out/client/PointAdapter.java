package com.rushcrew.order_service.infrastructure.adapter.out.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.out.PointPort;
import com.rushcrew.order_service.infrastructure.adapter.out.client.feign.PointFeignClient;
import com.rushcrew.order_service.infrastructure.dto.point.CancelPointRequest;
import com.rushcrew.order_service.infrastructure.dto.point.PointBalanceResponse;
import com.rushcrew.order_service.infrastructure.dto.point.UsePointRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointAdapter implements PointPort {

	private final PointFeignClient pointFeignClient;


	@Override
	public void usePoint(Long userId, String tempOrderId, Long pointUsed, UUID sagaId) {
		log.info("포인트 서비스 호출 (사용): userId={}, orderId={}, pointUsed={}",
			userId, tempOrderId, pointUsed);
		UsePointRequest request = UsePointRequest.builder()
			.userId(userId)
			.orderId(tempOrderId)
			.amount(pointUsed)
			.sagaId(sagaId.toString())
			.build();
		try {
			pointFeignClient.usePoint(request);
			log.info("포인트 사용 완료: orderId={}", request.orderId());
		} catch (Exception e) {
			log.error("포인트 사용 실패: orderId={}, error={}", request.orderId(), e.getMessage());
			throw e;
		}
	}

	@Override
	public void cancelPointUse(Long userId, String tempOrderId, UUID sagaId) {
		log.info("포인트 서비스 호출 (취소): userId={}, orderId={}", userId, tempOrderId);
		CancelPointRequest request = CancelPointRequest.builder()
			.userId(userId)
			.orderId(tempOrderId)
			.sagaId(sagaId.toString())
			.build();
		try {
			pointFeignClient.cancelPointUse(request);
			log.info("포인트 취소 완료: orderId={}", request.orderId());
		} catch (Exception e) {
			log.error("포인트 취소 실패: orderId={}, error={}", request.orderId(), e.getMessage());
			throw e;
		}
	}
}
