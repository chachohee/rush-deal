package com.rushcrew.user_service.point.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.user_service.point.application.PointService;
import com.rushcrew.user_service.point.application.command.CancelOrderCommand;
import com.rushcrew.user_service.point.application.command.CreatePendingPointCommand;
import com.rushcrew.user_service.point.application.command.RefundPointCommand;
import com.rushcrew.user_service.point.infrastructure.messaging.dto.PointEarnRequestedEvent;
import com.rushcrew.user_service.point.infrastructure.messaging.dto.PointRefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointEventConsumer {

    private final PointService pointService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "point.earn.requested", groupId = "point-service-group")
    public void consumePointEarnRequested(
        @Payload String message,
        Acknowledgment acknowledgment
    ) throws Exception {
            PointEarnRequestedEvent event = objectMapper.readValue(
                message,
                PointEarnRequestedEvent.class
            );

            CreatePendingPointCommand command = new CreatePendingPointCommand(
                event.userId(),
                event.orderId(),
                event.finalAmount(),
                event.sagaId()
            );

            pointService.createPendingPoint(command);

            acknowledgment.acknowledge();
    }

	// 주문 취소 시에 예비 포인트 적립 취소
    @KafkaListener(topics = "point.refund.requested", groupId = "point-service-group")
    public void consumePointRefundRequested(
        @Payload String message,
        Acknowledgment acknowledgment
    ) throws Exception {

        PointRefundRequestedEvent event = objectMapper.readValue(
                message,
                PointRefundRequestedEvent.class
            );

            CancelOrderCommand command = new CancelOrderCommand(
                event.userId(),
                event.orderId(),
                event.sagaId()
            );


            pointService.cancelOrder(command);

            acknowledgment.acknowledge();
    }

	@KafkaListener(topics = "point.use.cancel.requested", groupId = "point-service-group")
	public void consumePointUseCancellRequested(
		@Payload String message,
		Acknowledgment acknowledgment
	) throws Exception {

		PointRefundRequestedEvent event = objectMapper.readValue(
			message,
			PointRefundRequestedEvent.class
		);

		// USE_PENDING 포인트 환불 로직
		RefundPointCommand command = new RefundPointCommand(
			event.userId(),
			event.orderId(),
			event.sagaId()
		);

		pointService.refundUsedPoints(command);

		acknowledgment.acknowledge();

		log.info("포인트 환불 요청 처리 완료: userId={}, orderId={}",
			event.userId(), event.orderId());
	}
}
