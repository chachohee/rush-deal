package com.rushcrew.order_service.presentation.api.batch;

import com.rushcrew.order_service.global.security.model.UserDetailsImpl;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rushcrew.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchController {

	private final JobLauncher jobLauncher;
	private final Job autoConfirmPurchaseJob;

	/**
	 * 자동 구매확정 배치 수동 실행 - 관리자 전용
	 */
	@PostMapping("/auto-confirm")
	@PreAuthorize("hasRole('MASTER')")
	public ApiResponse<String> runAutoConfirmBatch() {
		log.info("====== 트리거 매뉴얼: 자동 구매확정 배치 ======");

		try {
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			jobLauncher.run(autoConfirmPurchaseJob, params);

			return ApiResponse.success("배치 작업 시작 성공");

		} catch (Exception e) {
			log.error("====== 배치 작업 시작 실패 ======", e);
			return ApiResponse.error("배치 작업 시작 실패. 에러 메시지: " + e.getMessage());
		}
	}
}
