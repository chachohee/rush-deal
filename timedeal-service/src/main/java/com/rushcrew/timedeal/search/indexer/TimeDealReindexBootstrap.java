package com.rushcrew.timedeal.search.indexer;

import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.search.document.TimeDealDocument;
import com.rushcrew.timedeal.search.repository.TimeDealSearchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
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

    private final TimeDealSearchRepository searchRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void reindexOnStartup() {
        long indexed = searchRepository.count();
        long total = (Long) entityManager
            .createQuery("SELECT COUNT(t) FROM TimeDeal t")
            .getSingleResult();

        if (indexed >= total) {
            log.info("[Search] Index already populated ({} docs, {} entities) — skipping bootstrap reindex",
                indexed, total);
            return;
        }

        log.info("[Search] Bootstrap reindex starting: {} → {}", indexed, total);
        List<TimeDeal> all = entityManager
            .createQuery("SELECT t FROM TimeDeal t", TimeDeal.class)
            .getResultList();
        searchRepository.saveAll(all.stream().map(TimeDealDocument::from).toList());
        log.info("[Search] Bootstrap reindex finished: {} docs", all.size());
    }
}
