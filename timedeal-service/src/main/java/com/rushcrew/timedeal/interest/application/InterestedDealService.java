package com.rushcrew.timedeal.interest.application;

import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.interest.domain.InterestedDeal;
import com.rushcrew.timedeal.interest.domain.InterestedDealRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterestedDealService {

    private final InterestedDealRepository interestedDealRepository;
    private final TimeDealRepository timeDealRepository;

    @Transactional
    public void register(Long userId, UUID timeDealId) {
        if (interestedDealRepository.existsByUserIdAndTimeDealId(userId, timeDealId)) {
            return;
        }
        interestedDealRepository.save(InterestedDeal.create(userId, timeDealId));
    }

    @Transactional
    public void unregister(Long userId, UUID timeDealId) {
        interestedDealRepository.deleteByUserIdAndTimeDealId(userId, timeDealId);
    }

    @Transactional(readOnly = true)
    public boolean isInterested(Long userId, UUID timeDealId) {
        return interestedDealRepository.existsByUserIdAndTimeDealId(userId, timeDealId);
    }

    @Transactional(readOnly = true)
    public List<TimeDealResult> listMyInterested(Long userId) {
        List<UUID> ids = interestedDealRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(InterestedDeal::getTimeDealId)
            .toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, TimeDeal> byId = timeDealRepository.findAllById(ids).stream()
            .collect(java.util.stream.Collectors.toMap(TimeDeal::getId, Function.identity()));
        return ids.stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .map(td -> new TimeDealResult(
                td.getId(),
                td.getTimeDealInfo().getTitle(),
                td.getTimeDealInfo().getDescription(),
                td.getPrice().getAmount(),
                td.getPeriod().getStartAt(),
                td.getPeriod().getEndAt(),
                td.getStatus(),
                td.getImageUrl()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findUserIdsByTimeDealIds(List<UUID> timeDealIds) {
        return interestedDealRepository.findByTimeDealIdIn(timeDealIds).stream()
            .map(InterestedDeal::getUserId)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findUserIdsByTimeDealId(UUID timeDealId) {
        return interestedDealRepository.findByTimeDealIdIn(List.of(timeDealId)).stream()
            .map(InterestedDeal::getUserId)
            .toList();
    }

    @Transactional(readOnly = true)
    public long count(UUID timeDealId) {
        return interestedDealRepository.countByTimeDealId(timeDealId);
    }
}
