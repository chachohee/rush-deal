package com.rushcrew.timedeal.infrastructure.kafka.dto;

import java.time.Instant;
import java.util.UUID;

public record TimeDealEndMessage(
    UUID timeDealId,
    UUID productId,
    Instant endAt
) {

}
