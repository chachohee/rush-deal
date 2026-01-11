package com.rushcrew.timedeal.infrastructure.kafka.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.timedeal.application.port.out.event.StockReservationFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockRestoreFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockSoldOutEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishStockReserved(StockReservedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("stock.reserved", event.sagaId(), message);
            log.info("[Saga-{}] stock.reserved 이벤트 발행 완료", event.sagaId());
        } catch (Exception e) {
            log.error("[Saga-{}] stock.reserved 이벤트 발행 실패", event.sagaId(), e);
            throw new RuntimeException("이벤트 발행 실패", e);
        }
    }

    public void publishStockReservationFailed(StockReservationFailedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("stock.reservation.failed", event.sagaId(), message);
            log.info("[Saga-{}] stock.reservation.failed 이벤트 발행 완료", event.sagaId());
        } catch (Exception e) {
            log.error("[Saga-{}] stock.reservation.failed 이벤트 발행 실패", event.sagaId(), e);
            throw new RuntimeException("이벤트 발행 실패", e);
        }
    }

	public void publishStockRestoreFailed(StockRestoreFailedEvent event) {
		try {
			String message = objectMapper.writeValueAsString(event);
			kafkaTemplate.send("stock.restore.failed", event.sagaId(), message);
			log.info("[Saga-{}] stock.restore.failed 이벤트 발행 완료", event.sagaId());
		} catch (Exception e) {
			log.error("[Saga-{}] stock.restore.failed 이벤트 발행 실패", event.sagaId(), e);
			throw new RuntimeException("이벤트 발행 실패", e);
		}
	}

    public void publishStockSoldOut(StockSoldOutEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("product-sold-out", event.productId().toString(), message);
            log.info("product-sold-out 이벤트 발행 완료 - productId={}", event.productId());
        } catch (Exception e) {
            log.error("product-sold-out 이벤트 발행 실패 - productId={}", event.productId(), e);
        }
    }
}
