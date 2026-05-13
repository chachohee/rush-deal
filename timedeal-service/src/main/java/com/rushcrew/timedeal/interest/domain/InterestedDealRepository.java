package com.rushcrew.timedeal.interest.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestedDealRepository extends JpaRepository<InterestedDeal, Long> {

    boolean existsByUserIdAndTimeDealId(Long userId, UUID timeDealId);

    void deleteByUserIdAndTimeDealId(Long userId, UUID timeDealId);

    List<InterestedDeal> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<InterestedDeal> findByTimeDealIdIn(List<UUID> timeDealIds);

    long countByTimeDealId(UUID timeDealId);
}
