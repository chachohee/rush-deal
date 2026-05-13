package com.rushcrew.timedeal.interest.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import com.rushcrew.timedeal.infrastructure.kafka.dto.TimeDealEndingSoonMessage;
import com.rushcrew.timedeal.interest.application.InterestedDealService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeDealEndingSoonScheduler {

    private static final int NOTIFY_MINUTES_BEFORE = 10;
    private static final String TOPIC = "timedeal.ending.soon";

    @PersistenceContext
    private EntityManager entityManager;

    private final InterestedDealService interestedDealService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final Set<UUID> notified = java.util.Collections.synchronizedSet(new HashSet<>());

    @Scheduled(fixedDelay = 60_000)
    @Transactional(readOnly = true)
    public void scanEndingSoon() {
        Instant now = Instant.now();
        Instant horizon = now.plus(NOTIFY_MINUTES_BEFORE, ChronoUnit.MINUTES);

        List<TimeDeal> candidates = entityManager.createQuery("""
                SELECT td FROM TimeDeal td
                WHERE td.status = :status
                  AND td.period.endAt BETWEEN :now AND :horizon
                  AND td.deletedAt IS NULL
            """, TimeDeal.class)
            .setParameter("status", TimeDealStatus.IN_PROGRESS)
            .setParameter("now", now)
            .setParameter("horizon", horizon)
            .getResultList();

        for (TimeDeal td : candidates) {
            if (!notified.add(td.getId())) continue;
            List<Long> userIds = interestedDealService.findUserIdsByTimeDealId(td.getId());

            long minutesLeft = ChronoUnit.MINUTES.between(now, td.getPeriod().getEndAt());
            TimeDealEndingSoonMessage message = new TimeDealEndingSoonMessage(
                td.getId(),
                td.getTimeDealInfo().getTitle(),
                td.getPeriod().getEndAt(),
                (int) Math.max(1, minutesLeft),
                td.getTimeDealInfo().getSellerId(),
                userIds
            );
            try {
                kafkaTemplate.send(TOPIC, td.getId().toString(),
                    objectMapper.writeValueAsString(message));
                log.info("[Interest] ENDING_SOON fanout - timeDealId={}, users={}",
                    td.getId(), userIds.size());
            } catch (Exception e) {
                log.error("[Interest] ENDING_SOON 발행 실패: {}", e.getMessage(), e);
                notified.remove(td.getId());
            }
        }
    }
}
