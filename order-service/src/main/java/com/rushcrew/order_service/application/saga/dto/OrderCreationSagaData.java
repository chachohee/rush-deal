package com.rushcrew.order_service.application.saga.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.domain.vo.ProductSnapshot;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)	// 알 수 없는 필드는 무시하고 필요한 필드만 역직렬화
public class OrderCreationSagaData {

	/**
	 * 주문 요청 원본
	 */
	private CreateOrderCommand command;

	/**
	 * ValidateStockStep에서 확정된 상품 스냅샷
	 * (타임딜, 옵션, 가격, 할인 정보 포함)
	 */
	private List<ProductSnapshot> productSnapshots;

	/**
	 * 주문 생성 결과 (실제 주문 ID)
	 */
	private UUID orderId;
	private String queueToken;

	/**
	 * 포인트 사용 여부 확인
	 */
	public boolean hasPointUsage() {
		return command != null
			&& command.pointUsed() != null
			&& command.pointUsed() > 0;
	}
}
