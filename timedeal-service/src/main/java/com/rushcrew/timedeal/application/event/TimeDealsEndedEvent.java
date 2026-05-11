package com.rushcrew.timedeal.application.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TimeDealsEndedEvent(
    Map<String, TimeDealEndInfo> timeDealEndMap
) {
    public record TimeDealEndInfo(UUID productId, Instant endAt) {}
}
