package com.rushcrew.timedeal.application.listener;

import com.rushcrew.timedeal.application.event.TimeDealsEndedEvent;
import com.rushcrew.timedeal.application.event.TimeDealsStartedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservationFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockRestoreFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockSoldOutEvent;
import com.rushcrew.timedeal.application.port.out.event.TimeDealEndedEvent;
import com.rushcrew.timedeal.application.port.out.event.TimeDealStartedEvent;
import com.rushcrew.timedeal.domain.port.TimeDealCache;
import com.rushcrew.timedeal.domain.port.TimeDealQueueKey;
import com.rushcrew.timedeal.infrastructure.kafka.publisher.StockEventProducer;
import com.rushcrew.timedeal.infrastructure.kafka.publisher.TimeDealEventProducer;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeDealEventListener {

    private final StockEventProducer stockEventProducer;
    private final TimeDealEventProducer timeDealEventProducer;
    private final TimeDealCache timeDealCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStockSoldOut(StockSoldOutEvent event) {
        stockEventProducer.publishStockSoldOut(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTimeDealStart(TimeDealsStartedEvent event) {
        try {
            if (!event.timeDealStartMap().isEmpty()) {
                timeDealCache.removeTimedOut(
                    TimeDealQueueKey.START,
                    new ArrayList<>(event.timeDealStartMap().keySet())
                );
            }
        } catch (Exception e) {
            log.warn("Redis 삭제 실패", e);
        }

        event.timeDealStartMap().forEach((timeDealId, startAt) -> {
            try {
                timeDealEventProducer.publishTimeDealStart(
                    new TimeDealStartedEvent(UUID.fromString(timeDealId), startAt));
            } catch (Exception e) {
                log.error("Kafka 이벤트 발행 실패 - TimeDealId: {}", timeDealId, e);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTimeDealEnd(TimeDealsEndedEvent event) {
        try {
            if(!event.timeDealEndMap().isEmpty()) {
                timeDealCache.removeTimedOut(
                    TimeDealQueueKey.END,
                    new ArrayList<>(event.timeDealEndMap().keySet())
                );
            }
        } catch(Exception e) {
            log.warn("Redis 삭제 실패", e);
        }

        event.timeDealEndMap().forEach((timeDealId, info) -> {
            try {
                timeDealEventProducer.publishTimeDealEnd(
                    new TimeDealEndedEvent(UUID.fromString(timeDealId), info.productId(), info.endAt()));
            } catch (Exception e) {
                log.error("Kafka 이벤트 발행 실패 - TimeDealId: {}", timeDealId, e);
            }
        });
    }

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleStockReserved(StockReservedEvent event) {
		stockEventProducer.publishStockReserved(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleStockReservationFailed(StockReservationFailedEvent event) {
		log.info("[Saga-{}] StockReservationFailedEvent 처리 - Kafka 발행", event.sagaId());
		stockEventProducer.publishStockReservationFailed(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleStockRestoreFailed(StockRestoreFailedEvent event) {
		log.info("[Saga-{}] StockRestoreFailedEvent 처리 - Kafka 발행", event.sagaId());
		stockEventProducer.publishStockRestoreFailed(event);
	}
}
