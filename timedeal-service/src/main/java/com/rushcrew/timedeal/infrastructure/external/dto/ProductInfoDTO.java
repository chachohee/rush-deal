package com.rushcrew.timedeal.infrastructure.external.dto;

import java.util.List;
import java.util.UUID;

public record ProductInfoDTO(
    UUID productId,
    List<UUID> optionIds,
    Long sellerId,
    Long price,
    String imageUrl
) {

}
