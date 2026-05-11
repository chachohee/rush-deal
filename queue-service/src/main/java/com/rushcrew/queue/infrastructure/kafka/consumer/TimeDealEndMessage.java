package com.rushcrew.queue.infrastructure.kafka.consumer;

import java.time.Instant;
import java.util.UUID;

public record TimeDealEndMessage(
    UUID timeDealId,
    UUID productId,
    Instant endAt
) {}
