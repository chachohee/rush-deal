package com.rushcrew.order_service.presentation.api.command;

import java.util.Map;
import java.util.UUID;

import com.rushcrew.order_service.global.security.model.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.order_service.application.command.dto.command.CancelOrderCommand;
import com.rushcrew.order_service.application.command.dto.command.ConfirmPurchaseCommand;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.command.dto.command.RefundOrderCommand;
import com.rushcrew.order_service.application.command.dto.command.RequestPaymentCommand;
import com.rushcrew.order_service.application.command.dto.command.UpdateOrderCommand;
import com.rushcrew.order_service.application.command.dto.result.CancelOrderResult;
import com.rushcrew.order_service.application.command.dto.result.ConfirmPurchaseResult;
import com.rushcrew.order_service.application.command.dto.result.CreateOrderResult;
import com.rushcrew.order_service.application.command.dto.result.RefundOrderResult;
import com.rushcrew.order_service.application.command.dto.result.RequestPaymentResult;
import com.rushcrew.order_service.application.command.dto.result.UpdateOrderResult;
import com.rushcrew.order_service.application.command.usecase.CancelOrderUseCase;
import com.rushcrew.order_service.application.command.usecase.ConfirmPurchaseUseCase;
import com.rushcrew.order_service.application.command.usecase.CreateOrderUseCase;
import com.rushcrew.order_service.application.command.usecase.RefundOrderUseCase;
import com.rushcrew.order_service.application.command.usecase.RequestPaymentUseCase;
import com.rushcrew.order_service.application.command.usecase.UpdateOrderUseCase;
import com.rushcrew.order_service.presentation.mapper.CreateOrderCommandMapper;
import com.rushcrew.order_service.presentation.mapper.UpdateOrderCommandMapper;
import com.rushcrew.order_service.presentation.mapper.UpdateOrderResultMapper;
import com.rushcrew.order_service.presentation.dto.request.CreateOrderRequest;
import com.rushcrew.order_service.presentation.dto.request.UpdateOrderRequest;
import com.rushcrew.order_service.presentation.dto.response.CancelOrderResponse;
import com.rushcrew.order_service.presentation.dto.response.ConfirmPurchaseResponse;
import com.rushcrew.order_service.presentation.dto.response.RefundOrderResponse;
import com.rushcrew.order_service.presentation.dto.response.RequestPaymentResponse;
import com.rushcrew.order_service.presentation.dto.response.UpdateOrderResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController {

	private final CreateOrderUseCase createOrderUseCase;
	private final CreateOrderCommandMapper createOrderCommandMapper;
	private final RequestPaymentUseCase requestPaymentUseCase;
	private final ConfirmPurchaseUseCase confirmPurchaseUseCase;
	private final UpdateOrderUseCase updateOrderUseCase;
	private final UpdateOrderCommandMapper updateOrderCommandMapper;
	private final UpdateOrderResultMapper updateOrderResultMapper;
	private final CancelOrderUseCase cancelOrderUseCase;
	private final RefundOrderUseCase refundOrderUseCase;

	/**
	 * 주문 생성 API (Saga 접수)
	 */
	@PostMapping
	@PreAuthorize("hasAnyRole('USER', 'MASTER', 'SELLER')")
	public ApiResponse<CreateOrderResult> createOrder(
		@Valid @RequestBody CreateOrderRequest request,
		@AuthenticationPrincipal UserDetailsImpl userDetails,
		@RequestHeader("X-Queue-Token") String queueToken
	) {
		CreateOrderCommand command = createOrderCommandMapper.toCommand(request, userDetails.userId(), userDetails.role(), queueToken);
		CreateOrderResult result = createOrderUseCase.createOrder(command);	// Saga 접수
		return ApiResponse.success(result);	// 반환 (PROCESSING 상태 + sagaId)
	}

	/**
	 * 결제 요청 API
	 */
	@PostMapping("/{orderId}/payment")
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<RequestPaymentResponse> requestPayment(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		RequestPaymentCommand command = RequestPaymentCommand.of(orderId, userDetails.userId());
		RequestPaymentResult result = requestPaymentUseCase.requestPayment(command);
		RequestPaymentResponse response = RequestPaymentResponse.from(result);
		return ApiResponse.success(response);
	}

	/**
	 * 구매확정 API
	 */
	@PostMapping("/{orderId}/confirm")
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<ConfirmPurchaseResponse> confirmPurchase(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		ConfirmPurchaseCommand command = ConfirmPurchaseCommand.of(orderId, userDetails.userId());
		ConfirmPurchaseResult result = confirmPurchaseUseCase.confirmPurchase(command);
		ConfirmPurchaseResponse response = ConfirmPurchaseResponse.from(result);
		return ApiResponse.success(response);
	}

	/**
	 * 주문 수정 API
	 */
	@PutMapping("/{orderId}")
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<UpdateOrderResponse> updateOrder(
		@PathVariable UUID orderId,
		@Valid @RequestBody UpdateOrderRequest request,
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		UpdateOrderCommand command = updateOrderCommandMapper.toCommand(orderId, userDetails.userId(), request);
		UpdateOrderResult result = updateOrderUseCase.updateOrder(command);
		UpdateOrderResponse response = updateOrderResultMapper.toResponse(result);
		return ApiResponse.success(response);
	}

	/**
	 * 주문 취소 API
	 */
	@PostMapping("/{orderId}/cancel")
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<CancelOrderResponse> cancelOrder(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal UserDetailsImpl userDetails
	) {
		CancelOrderCommand command = CancelOrderCommand.ofAdmin(orderId, userDetails.userId(), "관리자에 의한 취소");
		CancelOrderResult result = cancelOrderUseCase.cancelOrder(command);
		CancelOrderResponse response = CancelOrderResponse.from(result);
		return ApiResponse.success(response);
	}

	/**
	 * 주문 환불 API
	 */
	@PostMapping("/{orderId}/refund")
	@PreAuthorize("hasAnyRole('USER', 'MASTER')")
	public ApiResponse<RefundOrderResponse> refundOrder(
		@PathVariable UUID orderId,
		@AuthenticationPrincipal UserDetailsImpl userDetails,
		@RequestBody(required = false) Map<String, String> requestBody
	) {
		String reason = requestBody != null ? requestBody.get("reason") : null;
		RefundOrderCommand command = RefundOrderCommand.of(orderId, userDetails.userId(), reason);
		RefundOrderResult result = refundOrderUseCase.refundOrder(command);
		RefundOrderResponse response = RefundOrderResponse.from(result);
		return ApiResponse.success(response);
	}

}
