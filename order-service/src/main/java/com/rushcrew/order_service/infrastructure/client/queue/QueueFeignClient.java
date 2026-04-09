package com.rushcrew.order_service.infrastructure.client.queue;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.rushcrew.common.dto.ApiResponse;

@FeignClient(name = "queue-service")
public interface QueueFeignClient {

	@GetMapping("/api/v1/internal/queues/tokens/verify")
	ApiResponse<Boolean> validateToken(
		@RequestParam("productId") String productId,
		@RequestHeader("X-Queue-Token") String queueToken,
		@RequestHeader("X-User-Id") Long userId,
		@RequestHeader("X-User-Role") String role
	);

}
