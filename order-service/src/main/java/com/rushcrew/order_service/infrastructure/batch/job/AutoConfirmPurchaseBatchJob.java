package com.rushcrew.order_service.infrastructure.batch.job;

import java.time.Instant;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.rushcrew.order_service.application.port.out.PointEventPort;
import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.infrastructure.persistence.order.OrderJpaRepository;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AutoConfirmPurchaseBatchJob {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final OrderJpaRepository orderJpaRepository;
	private final PointEventPort pointEventPort;
	private final EntityManagerFactory entityManagerFactory;

	/** 자동 구매확정 Job */
	@Bean
	public Job autoConfirmPurchaseJob() {
		return new JobBuilder("autoConfirmPurchaseJob", jobRepository)
			.start(autoConfirmPurchaseStep())
			.build();
	}

	/** 자동 구매확정 Step - reader는 @Bean 아님, 매 실행마다 새 인스턴스 생성 */
	@Bean
	public Step autoConfirmPurchaseStep() {
		return new StepBuilder("autoConfirmPurchaseStep", jobRepository)
			.<Order, Order>chunk(100, transactionManager)
			.reader(autoConfirmTargetOrderReader())  // @Bean 아님: Job 실행 시마다 Instant.now() 재평가
			.processor(autoConfirmProcessor())
			.writer(autoConfirmWriter())
			.build();
	}

	/**
	 * Reader: 자동 구매확정 대상 주문 조회
	 *
	 * JpaCursorItemReader 사용 이유:
	 * - RepositoryItemReader(페이지 기반)는 Processor/Writer에서 상태를 변경(PAID→PURCHASE_CONFIRMED)하면
	 *   다음 페이지 오프셋이 밀려 일부 레코드가 스킵되는 문제 발생
	 * - JpaCursorItemReader는 커서 기반이므로 처리 중 상태 변경에 영향받지 않음
	 * - @Bean 대신 메서드 직접 호출로 Job 실행 시마다 Instant.now() 재평가
	 */
	public JpaCursorItemReader<Order> autoConfirmTargetOrderReader() {
		return new JpaCursorItemReaderBuilder<Order>()
			.name("autoConfirmTargetOrderReader")
			.entityManagerFactory(entityManagerFactory)
			.queryString("""
				SELECT o FROM Order o
				WHERE o.status = :status
				AND o.autoConfirmScheduledAt < :now
				ORDER BY o.orderedAt ASC
				""")
			.parameterValues(Map.of(
				"status", OrderStatus.PAID,
				"now", Instant.now()
			))
			.build();
	}

	/** Processor: 구매확정 처리 */
	@Bean
	public ItemProcessor<Order, Order> autoConfirmProcessor() {
		return order -> {
			log.info("자동 구매확정 처리 중 - 주문 ID: {}, 사용자 ID: {}", order.getOrderId(), order.getUserId());
			order.confirmPurchase("자동 구매확정");
			return order;
		};
	}

	/** Writer: DB 저장 및 포인트 적립 이벤트 발행 */
	@Bean
	public ItemWriter<Order> autoConfirmWriter() {
		return orders -> {
			orderJpaRepository.saveAll(orders);
			for (Order order : orders) {
				pointEventPort.publishPointEarnRequested(
					order.getUserId(),
					order.getOrderId(),
					order.getFinalAmount(),
					order.getSagaId(),
					"자동 구매확정"
				);
				log.info("자동 구매확정 완료 + 포인트 적립 요청 - 주문 ID: {}, 사용자 ID: {}",
					order.getOrderId(), order.getUserId());
			}
			log.info("총 {}건의 주문을 자동 구매확정 처리했습니다.", orders.size());
		};
	}
}
