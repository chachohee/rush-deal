package com.rushcrew.timedeal.search.listener;

import com.rushcrew.timedeal.application.event.TimeDealCreatedEvent;
import com.rushcrew.timedeal.application.event.TimeDealUpdatedEvent;
import com.rushcrew.timedeal.application.event.TimeDealsEndedEvent;
import com.rushcrew.timedeal.application.event.TimeDealsStartedEvent;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import com.rushcrew.timedeal.search.indexer.TimeDealIndexer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeDealIndexEventListener {

    private final TimeDealIndexer indexer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(TimeDealCreatedEvent event) {
        indexer.index(event.timeDealId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpdated(TimeDealUpdatedEvent event) {
        indexer.index(event.timeDealId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStarted(TimeDealsStartedEvent event) {
        event.timeDealStartMap().keySet().forEach(id ->
            indexer.updateStatus(UUID.fromString(id), TimeDealStatus.IN_PROGRESS));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnded(TimeDealsEndedEvent event) {
        event.timeDealEndMap().keySet().forEach(id ->
            indexer.updateStatus(UUID.fromString(id), TimeDealStatus.ENDED));
    }
}
