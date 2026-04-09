package com.rushcrew.order_service.infrastructure.client.timedeal;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.dto.TimeDealInfo;
import com.rushcrew.order_service.application.port.dto.TimeDealStatus;
import com.rushcrew.order_service.application.port.dto.TimeDealStockDetail;
import com.rushcrew.order_service.application.port.dto.TimeDealStockStatus;
import com.rushcrew.order_service.application.port.out.TimeDealStockPort;
import com.rushcrew.order_service.infrastructure.client.timedeal.TimeDealStockFeignClient;
import com.rushcrew.order_service.infrastructure.client.timedeal.dto.TimeDealResponse;
import com.rushcrew.order_service.infrastructure.client.timedeal.dto.TimeDealStockResponse;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TimeDealStockAdapter implements TimeDealStockPort {

	private final TimeDealStockFeignClient feignClient;

	@Override
	public TimeDealInfo getTimeDeal(@NonNull UUID timeDealId) {
		TimeDealResponse response = feignClient.getTimeDeal(timeDealId.toString());
		TimeDealStatus status = TimeDealStatus.from(response.getStatus());
		return TimeDealInfo.builder()
			.timeDealId(UUID.fromString(response.getTimeDealId()))
			.title(response.getTitle())
			.status(status)
			.discountPrice(response.getDiscountPrice())
			.limitQuantity(response.getLimitQuantity())
			.build();
	}

	@Override
	public TimeDealStockDetail getTimeDealStockDetail(@NonNull UUID timeDealStockId) {
		TimeDealStockResponse response = feignClient.getTimeDealStockDetail(timeDealStockId.toString());
		TimeDealStockStatus status = TimeDealStockStatus.from(response.getStatus());
		return TimeDealStockDetail.builder()
			.timeDealStockId(UUID.fromString(response.getTimeDealStockId()))
			.availableStock(response.getAvailableStock())
			.reservedStock(response.getReservedStock())
			.soldStock(response.getSoldStock())
			.status(status)
			.productId(UUID.fromString(response.getProductId()))
			.build();
	}
}
