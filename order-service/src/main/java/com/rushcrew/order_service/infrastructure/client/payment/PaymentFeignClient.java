package com.rushcrew.order_service.infrastructure.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentCancelRequest;
import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentPrepareResponse;
import com.rushcrew.order_service.infrastructure.client.payment.dto.PaymentRequest;

@FeignClient(name = "payment-service")
public interface PaymentFeignClient {
	@PostMapping("/api/v1/payments")
	PaymentPrepareResponse requestPayment(@RequestBody PaymentRequest request);

	@PostMapping("/api/v1/payments/cancel")
	void cancelPayment(@RequestBody PaymentCancelRequest request);
}
