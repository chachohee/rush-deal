package com.rushcrew.order_service.infrastructure.client.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.out.PaymentPort;
import com.rushcrew.order_service.infrastructure.client.payment.PaymentFeignClient;
import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentCancelRequest;
import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentPrepareResponse;
import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAdapter implements PaymentPort {

	private final PaymentFeignClient paymentFeignClient;

	@Override
	public boolean requestPayment(UUID orderId, Long userId, BigDecimal finalAmount) {
		try {
			log.info("결제 요청: orderId={}, userId={}, finalAmount={}", orderId, userId, finalAmount);

			PaymentRequest request = PaymentRequest.builder()
				.orderId(orderId)
				.totalAmount(finalAmount)
				.build();

			PaymentPrepareResponse paymentResponse = paymentFeignClient.requestPayment(request);

			// PaymentPrepareResponse의 status가 "결제요청"이면 성공으로 간주
			// paymentId가 존재하면 결제 준비가 성공한 것으로 간주
			boolean success = paymentResponse.paymentId() != null && 
				(paymentResponse.status() == null || "결제요청".equals(paymentResponse.status()));

			if (success) {
				log.info("결제 요청 성공: orderId={}, paymentId={}", orderId, paymentResponse.paymentId());
				return true;
			} else {
				log.warn("결제 요청 실패: orderId={}, status={}", orderId, paymentResponse.status());
				return false;
			}

		} catch (Exception e) {
			log.error("결제 요청 중 오류 발생: orderId={}, userId={}", orderId, userId, e);
			throw new RuntimeException("결제 요청 실패: " + e.getMessage(), e);
		}
	}

	@Override
	public void cancelPayment(UUID orderId, Long userId) {
		try {
			log.info("결제 취소 요청: orderId={}, userId={}", orderId, userId);

			PaymentCancelRequest request = PaymentCancelRequest.builder()
				.orderId(orderId)
				.userId(userId)
				.build();
			paymentFeignClient.cancelPayment(request);

			log.info("결제 취소 성공: orderId={}", orderId);

		} catch (feign.FeignException.NotFound e) {
			log.warn("결제 정보를 찾을 수 없음: orderId={}", orderId);
			// 주문 서비스에서는 환불 처리를 계속 진행할 수 있도록 예외를 던지지 않음
		} catch (Exception e) {
			log.error("결제 취소 중 오류 발생: orderId={}, userId={}", orderId, userId, e);
			// 결제 취소 실패 시 로그만 남기고 계속 진행
			log.warn("결제 취소 실패했지만 주문 환불 처리는 계속 진행: orderId={}", orderId);
		}
	}
}
