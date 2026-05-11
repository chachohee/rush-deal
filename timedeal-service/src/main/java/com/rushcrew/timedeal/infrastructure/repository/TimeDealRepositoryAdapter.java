package com.rushcrew.timedeal.infrastructure.repository;

import com.rushcrew.timedeal.application.result.TimeDealForOrderResult;
import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.entity.TimeDealProduct;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeDealRepositoryAdapter implements TimeDealRepository {

    private final TimeDealJpaRepository timeDealJpaRepository;

    @Override
    public void save(TimeDeal timeDeal) {
        timeDealJpaRepository.save(timeDeal);
    }

    @Override
    public Optional<TimeDeal> findById(UUID timeDealId) {
        return timeDealJpaRepository.findById(timeDealId);
    }

    @Override
    public Page<TimeDealResult> findNotEndedByStatus(TimeDealStatus status, Pageable pageable) {
        return timeDealJpaRepository.findNotEndedByStatus(status, pageable);
    }

    @Override
    public Page<TimeDealResult> findAllByStatus(TimeDealStatus status, Pageable pageable) {
        return timeDealJpaRepository.findAllByStatus(status, pageable);
    }

    @Override
    public Optional<TimeDeal> findByIdAndStatusNot(
        UUID timeDealId, TimeDealStatus timeDealStatus
    ) {
        return timeDealJpaRepository.findByIdAndStatusNot(timeDealId, timeDealStatus);
    }

    @Override
    public Optional<TimeDealProduct> findProductByProductId(UUID productId) {
        return timeDealJpaRepository.findProductByProductId(productId);
    }

    @Override
    public List<TimeDeal> findAllById(List<UUID> idList) {
        return timeDealJpaRepository.findAllById(idList);
    }

    @Override
    public Optional<TimeDealForOrderResult> findForOrder(UUID timeDealId) {
        return timeDealJpaRepository.findForOrderNative(timeDealId)
            .map(v -> new TimeDealForOrderResult(
                v.getTimeDealId(),
                v.getTitle(),
                v.getStatus(),
                v.getDiscountPrice(),
                v.getLimitQuantity()
            ));
    }
}
