package com.rushcrew.timedeal.application.result;

import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.time.Instant;
import java.util.UUID;

public record TimeDealResult(
    UUID id,
    String title,
    String description,
    Long price,
    Instant startAt,
    Instant endAt,
    TimeDealStatus status,
    String imageUrl
) {

}
