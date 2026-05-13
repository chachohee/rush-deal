package com.rushcrew.timedeal.presentation.dto.response;

import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import java.time.Instant;
import java.util.UUID;

public record TimeDealResponse(
    UUID id,
    String title,
    String description,
    Long price,
    Instant startAt,
    Instant endAt,
    TimeDealStatus status,
    String imageUrl
) {

    public static TimeDealResponse from(TimeDealResult result) {
        return new TimeDealResponse(
            result.id(),
            result.title(),
            result.description(),
            result.price(),
            result.startAt(),
            result.endAt(),
            result.status(),
            result.imageUrl()
        );
    }
}
