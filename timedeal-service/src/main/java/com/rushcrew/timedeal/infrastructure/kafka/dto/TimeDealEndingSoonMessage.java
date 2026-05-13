package com.rushcrew.timedeal.infrastructure.kafka.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimeDealEndingSoonMessage(
    UUID timeDealId,
    String title,
    Instant endAt,
    int minutesLeft,
    Long sellerId,
    List<Long> userIds
) {}
