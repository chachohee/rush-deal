package com.rushcrew.order_service.presentation.api.query;

import java.util.UUID;

import com.rushcrew.order_service.global.security.model.UserDetailsImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;
import com.rushcrew.order_service.application.query.dto.OrderListDto;
import com.rushcrew.order_service.application.query.dto.OrderSearchCriteria;
import com.rushcrew.order_service.application.query.dto.SellerOrderSummary;
import com.rushcrew.order_service.application.query.usecase.GetOrderDetailUseCase;
import com.rushcrew.order_service.application.query.usecase.GetOrderListUseCase;
import com.rushcrew.order_service.application.query.usecase.GetSellerOrderSummaryUseCase;
import com.rushcrew.order_service.presentation.dto.response.OrderDetailResponse;
import com.rushcrew.order_service.presentation.dto.response.OrderListResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderQueryController {

	private final GetOrderDetailUseCase getOrderDetailUseCase;
	private final GetOrderListUseCase getOrderListUseCase;
	private final GetSellerOrderSummaryUseCase getSellerOrderSummaryUseCase;

	@GetMapping("/{orderId}")
	@PreAuthorize("hasAnyRole('USER', 'MASTER', 'SELLER')")
	public ApiResponse<OrderDetailResponse> getOrderDetail(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		OrderDetailDto dto = getOrderDetailUseCase.getOrderDetail(orderId, userDetails.userId(), userDetails.role());
		return ApiResponse.success(OrderDetailResponse.from(dto));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<Page<OrderListResponse>> getOrderList(
		@AuthenticationPrincipal UserDetailsImpl userDetails,
		Pageable pageable
	) {
		OrderSearchCriteria criteria = OrderSearchCriteria.builder()
			.userId(userDetails.userId())
			.build();
		Page<OrderListDto> orders = getOrderListUseCase.getOrderList(criteria, pageable);
		return ApiResponse.success(orders.map(OrderListResponse::from));
	}

	@GetMapping("/seller/me/summary")
	@PreAuthorize("hasAnyRole('SELLER', 'MASTER')")
	public ApiResponse<SellerOrderSummary> getSellerOrderSummary(
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		return ApiResponse.success(getSellerOrderSummaryUseCase.getSummary(userDetails.userId()));
	}
}
