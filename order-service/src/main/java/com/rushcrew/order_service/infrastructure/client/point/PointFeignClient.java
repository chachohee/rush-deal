package com.rushcrew.order_service.infrastructure.client.point;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.rushcrew.order_service.infrastructure.client.point.dto.CancelPointRequest;
import com.rushcrew.order_service.infrastructure.client.point.dto.PointBalanceResponse;
import com.rushcrew.order_service.infrastructure.client.point.dto.UsePointRequest;

@FeignClient(name = "user-service")
public interface PointFeignClient {

	/* 포인트 사용 */
	@PostMapping("/api/v1/points/use")
	void usePoint(@RequestBody UsePointRequest request);

	/* 포인트 사용 취소 */
	@PostMapping("/api/v1/points/order/cancel")
	void cancelPointUse(@RequestBody CancelPointRequest request);

	/* 포인트 잔액 조회 */
	@GetMapping("/api/v1/points/balance/{userId}")
	PointBalanceResponse getBalance(@PathVariable("userId") Long userId);
}
