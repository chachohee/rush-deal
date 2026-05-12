package com.rushcrew.timedeal.search.indexer;

import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import com.rushcrew.timedeal.search.document.TimeDealDocument;
import com.rushcrew.timedeal.search.repository.TimeDealSearchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeDealIndexer {

    private final TimeDealSearchRepository searchRepository;
    private final TimeDealRepository timeDealRepository;

    public void index(UUID timeDealId) {
        TimeDeal td = timeDealRepository.findById(timeDealId).orElse(null);
        if (td == null) {
            log.warn("[Search] Index skipped, timedeal not found: {}", timeDealId);
            return;
        }
        searchRepository.save(TimeDealDocument.from(td));
        log.info("[Search] Indexed timedeal: {} ({})", timeDealId, td.getStatus());
    }

    public void updateStatus(UUID timeDealId, TimeDealStatus status) {
        searchRepository.findById(timeDealId.toString()).ifPresentOrElse(doc -> {
            doc.setStatus(status.name());
            searchRepository.save(doc);
            log.info("[Search] Updated status: {} → {}", timeDealId, status);
        }, () -> index(timeDealId));
    }

    public void delete(UUID timeDealId) {
        searchRepository.deleteById(timeDealId.toString());
    }
}
