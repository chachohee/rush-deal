package com.rushcrew.timedeal.infrastructure.external.dto;

import java.util.UUID;

public record ProductSearchInfoDTO(
    UUID productId,
    String productName,
    String companyName,
    String category,
    String categoryLabel,
    String imageUrl
) {}
