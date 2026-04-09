package com.rushcrew.order_service.infrastructure.client.queue;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.order_service.application.port.out.QueuePort;
import com.rushcrew.order_service.infrastructure.client.queue.QueueFeignClient;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdapter implements QueuePort {

	private final QueueFeignClient feignClient;

	@Override
	public boolean validateToken(UUID productId, Long userId, String queueToken, String role) {
		try {
			ApiResponse<Boolean> response = feignClient.validateToken(
				productId.toString(),
				queueToken,
				userId,
				role
			);

			return Boolean.TRUE.equals(response.data());
		} catch (Exception e) {
			log.error("대기열 토큰 검증 실패", e);
			return false;
		}
	}
}
