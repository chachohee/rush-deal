package com.rushcrew.order_service.infrastructure.client.timedeal.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TimeDealResponse {

	@NonNull private String timeDealId;
	@NonNull private String title;
	@NonNull private String status;
	@NonNull private BigDecimal discountPrice;
	@NonNull private Long limitQuantity;
}
