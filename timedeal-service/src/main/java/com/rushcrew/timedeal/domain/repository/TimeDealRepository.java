package com.rushcrew.timedeal.domain.repository;

import com.rushcrew.timedeal.application.result.TimeDealForOrderResult;
import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.entity.TimeDealProduct;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TimeDealRepository {

    void save(TimeDeal timeDeal);

    Optional<TimeDeal> findById(UUID timeDealId);

    Page<TimeDealResult> findNotEndedByStatus(TimeDealStatus status, Pageable pageable);

    Page<TimeDealResult> findAllByStatus(TimeDealStatus status, Pageable pageable);

    Optional<TimeDeal> findByIdAndStatusNot(UUID timeDealId, TimeDealStatus timeDealStatus);

    Optional<TimeDealProduct> findProductByProductId(UUID productId);

    List<TimeDeal> findAllById(List<UUID> idList);

    Optional<TimeDealForOrderResult> findForOrder(UUID timeDealId);
}
