package com.rushcrew.order_service.infrastructure.client.timedeal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.rushcrew.order_service.infrastructure.client.timedeal.dto.TimeDealResponse;
import com.rushcrew.order_service.infrastructure.client.timedeal.dto.TimeDealStockResponse;

@FeignClient(name = "timedeal-service")
public interface TimeDealStockFeignClient {

	@GetMapping("/api/v1/timedeals/{timeDealId}/order")
	TimeDealResponse getTimeDeal(@PathVariable("timeDealId") String timeDealId);

	@GetMapping("/api/v1/stocks/{timeDealStockId}")
	TimeDealStockResponse getTimeDealStockDetail(@PathVariable("timeDealStockId") String timeDealStockId);
}
