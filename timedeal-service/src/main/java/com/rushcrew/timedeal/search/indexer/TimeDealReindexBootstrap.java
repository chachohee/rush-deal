package com.rushcrew.timedeal.search.indexer;

import com.rushcrew.timedeal.domain.entity.TimeDeal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeDealReindexBootstrap {

    private final TimeDealIndexer indexer;

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void reindexOnStartup() {
        long total = (Long) entityManager
            .createQuery("SELECT COUNT(t) FROM TimeDeal t")
            .getSingleResult();

        if (total == 0L) {
            log.info("[Search] No timedeals to index — skipping bootstrap reindex");
            return;
        }

        log.info("[Search] Bootstrap reindex starting: {} entities", total);
        List<UUID> ids = entityManager
            .createQuery("SELECT t.id FROM TimeDeal t", UUID.class)
            .getResultList();
        ids.forEach(indexer::index);
        log.info("[Search] Bootstrap reindex finished: {} docs", ids.size());
    }
}
