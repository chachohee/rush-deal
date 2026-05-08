package com.rushcrew.order_service.presentation.api.query;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.order_service.application.query.dto.OrderSagaResult;
import com.rushcrew.order_service.application.query.usecase.GetOrderSagaUseCase;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderSagaQueryController {

	private final GetOrderSagaUseCase getOrderSagaUseCase;

	@GetMapping("/saga/{sagaId}")
	@PreAuthorize("hasRole('MASTER')")
	public ApiResponse<OrderSagaResult> getSaga(
		@PathVariable UUID sagaId
	) {
		return ApiResponse.success(
			getOrderSagaUseCase.getSaga(sagaId)
		);
	}
}
