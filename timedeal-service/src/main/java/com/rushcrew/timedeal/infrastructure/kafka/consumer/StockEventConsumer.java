package com.rushcrew.timedeal.infrastructure.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.timedeal.application.command.ConfirmStockCommand;
import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.application.command.RestoreStockCommand;
import com.rushcrew.timedeal.application.port.out.event.StockReservationFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservedEvent;
import com.rushcrew.timedeal.application.result.ReserveStockResult;
import com.rushcrew.timedeal.application.service.StockService;
import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.Quantity;
import com.rushcrew.timedeal.infrastructure.kafka.dto.StockConfirmEvent;
import com.rushcrew.timedeal.infrastructure.kafka.dto.StockReserveEvent;
import com.rushcrew.timedeal.infrastructure.kafka.dto.StockRestoreEvent;
import com.rushcrew.timedeal.infrastructure.kafka.publisher.StockEventProducer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventConsumer {

    private final StockService stockService;
    private final ObjectMapper objectMapper;
    private final StockEventProducer stockEventProducer;

    // 주문 생성 -> 재고 예약
    @KafkaListener(topics = "stock.reservation.requested")
    public void reserve(String message) {
        StockReserveEvent event = null;
        try {
            log.info("재고 예약 메시지 수신: {}", message);

            event = objectMapper.readValue(message, StockReserveEvent.class);

            // 재고 예약 처리
            List<StockReservedEvent.ReservedStockItem> reservedItems = new ArrayList<>();

            for (StockReserveEvent.OrderItem item : event.orderItems()) {
                ReserveStockCommand command = new ReserveStockCommand(
                    OrderId.of(UUID.fromString(event.sagaId())),
                    UUID.fromString(item.timeDealStockId()),
                    Quantity.positive(item.quantity()),
                    event.userId()
                );

				// 재고 예약만 수행
				stockService.reserveStock(command);

				log.info(
					"재고 예약 요청 처리 완료: sagaId={}, stockId={}, quantity={}",
					event.sagaId(),
					item.timeDealStockId(),
					item.quantity()
				);
            }

        } catch (JsonProcessingException e) {
            log.error("재고 예약 메시지 파싱 실패: {}", message, e);
            throw new RuntimeException("재고 예약 메시지 파싱 실패", e);

        } catch (Exception e) {
            log.error("재고 예약 처리 실패: sagaId={}",
                event != null ? event.sagaId() : "unknown", e);

            if (event != null) {
                StockReservationFailedEvent failedEvent = StockReservationFailedEvent.of(
                    event.sagaId(),
                    event.productId(),
                    e.getMessage()
                );
                stockEventProducer.publishStockReservationFailed(failedEvent);
            }

            throw new RuntimeException("재고 예약 처리 실패", e);
        }
    }

    // 결제 -> 재고 확정
    @KafkaListener(topics = "payment.completed")
    public void confirm(String message) {
        try {
            log.info("재고 확정 메시지 수신: {}", message);

            StockConfirmEvent event = objectMapper.readValue(message, StockConfirmEvent.class);

            ConfirmStockCommand command = new ConfirmStockCommand(
                OrderId.of(event.orderId()),
                event.stockId(),
                Quantity.positive(event.quantity())
            );
            stockService.confirmStock(command);

            log.info("재고 확정 완료: orderId={}", event.orderId());

        } catch (JsonProcessingException e) {
            log.error("재고 확정 메시지 파싱 실패: {}", message, e);
            throw new RuntimeException("재고 확정 메시지 파싱 실패", e);
        }
    }

    // 주문 취소 -> 재고 복구
    @KafkaListener(topics = "order.cancelled")
    public void restore(String message) {
        try {
            log.info("재고 복구 메시지 수신: {}", message);

            StockRestoreEvent event = objectMapper.readValue(message, StockRestoreEvent.class);

            RestoreStockCommand command = new RestoreStockCommand(
                event.stockId(),
                Quantity.positive(event.quantity()),
                OrderId.of(event.orderId()),
                event.reason()
            );
            stockService.restoreStock(command);

            log.info("재고 복구 완료: orderId={}", event.orderId());

        } catch (JsonProcessingException e) {
            log.error("재고 복구 메시지 파싱 실패: {}", message, e);
            throw new RuntimeException("재고 복구 메시지 파싱 실패", e);
        }
    }

    // 에러 로깅을 위한 헬퍼 메서드
    private String extractSagaId(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            return node.has("sagaId") ? node.get("sagaId").asText() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
