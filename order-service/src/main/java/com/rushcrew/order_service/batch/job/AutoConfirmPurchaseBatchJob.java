package com.rushcrew.order_service.batch.job;

import java.time.Instant;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import com.rushcrew.order_service.application.port.out.PointEventPort;
import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.infrastructure.persistence.repository.OrderJpaRepository;

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

	/** 자동 구매확정 Job */
	@Bean
	public Job autoConfirmPurchaseJob() {
		return new JobBuilder("autoConfirmPurchaseJob", jobRepository)
			.start(autoConfirmPurchaseStep())
			.build();
	}

	/** 자동 구매확정 Step */
	@Bean
	public Step autoConfirmPurchaseStep() {
		return new StepBuilder("autoConfirmPurchaseStep", jobRepository)
			.<Order, Order>chunk(100, transactionManager)
			.reader(autoConfirmTargetOrderReader())
			.processor(autoConfirmProcessor())
			.writer(autoConfirmWriter())
			.build();
	}

	/** Reader: 자동 구매확정 대상 주문 조회 */
	@Bean
	public RepositoryItemReader<Order> autoConfirmTargetOrderReader() {
		return new RepositoryItemReaderBuilder<Order>()
			.name("autoConfirmTargetOrderReader")
			.repository(orderJpaRepository)
			.methodName("findAllByStatusAndAutoConfirmScheduledAtBefore")
			.arguments(OrderStatus.PAID, Instant.now())
			.pageSize(100)
			.sorts(Map.of("orderedAt", Sort.Direction.ASC))
			.build();
	}

	/** Processor: 구매확정 처리 */
	@Bean
	public ItemProcessor<Order, Order> autoConfirmProcessor() {
		return order -> {
			log.info("자동 구매확정 처리 중 - 주문 ID: {}, 사용자 ID: {}", order.getOrderId(), order.getUserId());
			// 주문 이력에 "자동 구매확정" 기록
			order.confirmPurchase("자동 구매확정");
			return order;
		};
	}

	/** Writer: DB 저장 및 포인트 적립 이벤트 발행 */
	@Bean
	public ItemWriter<Order> autoConfirmWriter() {
		return orders -> {
			// 1. 주문 저장 (OrderHistory도 cascade로 저장됨)
			orderJpaRepository.saveAll(orders);
			// 2. 각 주문에 대해 포인트 적립 요청
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
