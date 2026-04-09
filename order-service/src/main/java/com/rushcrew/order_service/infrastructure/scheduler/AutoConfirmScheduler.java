package com.rushcrew.order_service.infrastructure.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동 구매확정
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmScheduler {

	private final JobLauncher jobLauncher;
	private final Job autoConfirmPurchaseJob;

	// 10초마다 실행
	// @Scheduled(cron = "0/10 * * * * ?")
	// 1분마다 실행
	// @Scheduled(cron = "0 * * * * ?")
	// 매 시간 정각 실행
	@Scheduled(cron = "0 0 * * * ?")
	protected void executeAutoConfirmBatch() {
		log.info("====== 자동 구매확정 배치 작업 시작 ======");
		try {
			// Job parameters 생성 (매번 다른 파라미터로 실행되도록)
			JobParameters params = new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis())
				.toJobParameters();

			// Job 실행
			jobLauncher.run(autoConfirmPurchaseJob, params);

			log.info("====== 자동 구매확정 배치 작업 완료 ======");

		} catch (Exception e) {
			log.error("====== 자동 구매확정 배치 작업 실패 ======", e);
		}
	}
}
