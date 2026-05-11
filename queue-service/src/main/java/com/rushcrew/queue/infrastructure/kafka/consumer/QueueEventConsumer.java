package com.rushcrew.queue.infrastructure.kafka.consumer;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.queue.application.port.in.QueuePort;
import com.rushcrew.queue.application.port.in.SoldOutEvent;
import com.rushcrew.queue.application.port.in.TokenRemoveEvent;
import com.rushcrew.queue.application.service.QueueService;
import com.rushcrew.queue.common.QueueErrorCode;
import com.rushcrew.queue.domain.entity.QueuePolicy;
import com.rushcrew.queue.domain.repository.QueuePolicyRepository;
import com.rushcrew.queue.infrastructure.repository.RedisQueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueueEventConsumer {

    private final QueuePort queueService;
    private final RedisQueueRepository redisQueueRepository;
    private final QueuePolicyRepository queuePolicyRepository;

    public QueueEventConsumer(QueueService queueService, RedisQueueRepository redisQueueRepository,
        QueuePolicyRepository queuePolicyRepository) {
        this.queueService = queueService;
        this.redisQueueRepository = redisQueueRepository;
        this.queuePolicyRepository = queuePolicyRepository;
    }

    /**
     * 주문 완료 시 발행되는 토큰 만료 요청 이벤트를 수신
     * Topic: order-complete-token-remove
     */
    @KafkaListener(topics = "order-complete-token-remove", groupId = "queue-service-group")
    public void consumeTokenRemoveEvent(TokenRemoveEvent event, Acknowledgment ack) {
        log.info("[QUEUE:Kafka:Consume] 토큰 삭제 요청 수신 - UserId: {}, Token: {}", event.userId(), event.token());

        // 토큰 삭제 & 유저 인덱스 삭제
        // 여기서 예외 발생 시, try-catch가 없으므로 즉시 에러 핸들러로 넘어감 -> 재시도 시작
        queueService.exitQueue(
            event.productId(),
            event.token(),
            event.userId()
        );
        log.info("[QUEUE:Kafka:Success] 토큰 삭제 완료 - UserId: {}, ProductId: {}",
            event.userId(), event.productId());

        // 수동 커밋 실행 (성공 시에만)
        // KafkaConsumerConfig에 AckMode.MANUAL_IMMEDIATE가 설정되어 있으므로 필수
        ack.acknowledge();

        log.info("[QUEUE:Kafka:Success] 토큰 삭제 완료 및 Offset 커밋 - UserId: {}", event.userId());
    }

    @KafkaListener(topics = "product-sold-out", groupId = "queue-service-group")
    public void handleSoldOutEvent(SoldOutEvent event, Acknowledgment ack) {
        log.info("[QUEUE:Kafka:Consume] 상품 재고품절 이벤트 수신: - ProductId: {}", event.productId());

        QueuePolicy queuePolicy = queuePolicyRepository.findByProductId(event.productId())
            .orElseThrow(() -> new BusinessException(QueueErrorCode.NO_TIMEDEAL_PRODUCT));

        // Redis에 품절 정보 기록
        redisQueueRepository.setSoldOut(event.productId(), event.status(), queuePolicy.getTimePeriod().getEndTime());

        // 수동 커밋 실행 - KafkaConsumerConfig에 AckMode.MANUAL_IMMEDIATE가 설정되어 있으므로 필수
        ack.acknowledge();
        log.info("[QUEUE:Kafka:Success] 상품 재고 품절 레디스 등록 및 Offset 커밋 - ProductId: {}", event.productId());

        // (추후 선택사항) 현재 대기열에 있는 사람들에게 웹소켓 등으로 "품절되었습니다" 알림
    }
}
