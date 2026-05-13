package com.rushcrew.timedeal.interest.listener;

import com.rushcrew.timedeal.application.event.TimeDealsStartedEvent;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealStartNotifyMessage;
import com.rushcrew.timedeal.infrastructure.kafka.publisher.TimeDealEventProducer;
import com.rushcrew.timedeal.interest.application.InterestedDealService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterestNotifyListener {

    private final InterestedDealService interestedDealService;
    private final TimeDealRepository timeDealRepository;
    private final TimeDealEventProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimeDealsStarted(TimeDealsStartedEvent event) {
        event.timeDealStartMap().forEach((timeDealIdStr, startAt) -> {
            UUID timeDealId = UUID.fromString(timeDealIdStr);
            List<Long> userIds = interestedDealService.findUserIdsByTimeDealId(timeDealId);
            if (userIds.isEmpty()) return;

            TimeDeal td = timeDealRepository.findById(timeDealId).orElse(null);
            if (td == null) {
                log.warn("[Interest] 타임딜을 찾지 못해 알림 스킵: {}", timeDealId);
                return;
            }
            producer.publishTimeDealStartNotify(new TimeDealStartNotifyMessage(
                timeDealId,
                td.getTimeDealInfo().getTitle(),
                td.getPeriod().getStartAt(),
                td.getPeriod().getEndAt(),
                td.getPrice().getAmount(),
                userIds
            ));
        });
    }
}
