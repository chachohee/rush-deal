package com.rushcrew.timedeal.infrastructure.kafka.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimeDealStartNotifyMessage(
    UUID timeDealId,
    String title,
    Instant startAt,
    Instant endAt,
    Long price,
    List<Long> interestedUserIds
) {}
