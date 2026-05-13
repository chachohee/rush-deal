package com.rushcrew.timedeal.interest.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.timedeal.application.event.TimeDealsStartedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockSoldOutEvent;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealStartNotifyMessage;
import com.rushcrew.timedeal.infrastructure.kafka.publisher.TimeDealEventProducer;
import com.rushcrew.timedeal.interest.application.InterestedDealService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterestNotifyListener {

    private final InterestedDealService interestedDealService;
    private final TimeDealRepository timeDealRepository;
    private final TimeDealEventProducer producer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimeDealsStarted(TimeDealsStartedEvent event) {
        event.timeDealStartMap().forEach((timeDealIdStr, startAt) -> {
            UUID timeDealId = UUID.fromString(timeDealIdStr);
            TimeDeal td = timeDealRepository.findById(timeDealId).orElse(null);
            if (td == null) {
                log.warn("[Interest] 타임딜을 찾지 못해 알림 스킵: {}", timeDealId);
                return;
            }
            List<Long> userIds = interestedDealService.findUserIdsByTimeDealId(timeDealId);
            producer.publishTimeDealStartNotify(new TimeDealStartNotifyMessage(
                timeDealId,
                td.getTimeDealInfo().getTitle(),
                td.getPeriod().getStartAt(),
                td.getPeriod().getEndAt(),
                td.getPrice().getAmount(),
                td.getTimeDealInfo().getSellerId(),
                userIds
            ));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStockSoldOut(StockSoldOutEvent event) {
        TimeDeal td = timeDealRepository.findProductByProductId(event.productId())
            .map(p -> p.getTimeDeal()).orElse(null);
        if (td == null) {
            log.warn("[Interest] 매진 알림 — 타임딜 매핑 못 찾음 productId={}", event.productId());
            return;
        }
        List<Long> userIds = interestedDealService.findUserIdsByTimeDealId(td.getId());

        try {
            Map<String, Object> payload = Map.of(
                "timeDealId", td.getId(),
                "title", td.getTimeDealInfo().getTitle(),
                "soldOutAt", Instant.now().toString(),
                "sellerId", td.getTimeDealInfo().getSellerId(),
                "userIds", userIds
            );
            kafkaTemplate.send("timedeal.sold.out.notify", td.getId().toString(),
                objectMapper.writeValueAsString(payload));
            log.info("[Interest] SOLD_OUT fanout - timeDealId={}, seller={}, users={}",
                td.getId(), td.getTimeDealInfo().getSellerId(), userIds.size());
        } catch (Exception e) {
            log.error("[Interest] SOLD_OUT 발행 실패: {}", e.getMessage(), e);
        }
    }
}
