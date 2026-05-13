package com.rushcrew.timedeal.application.model;

import java.util.UUID;

public record ProductSearchInfo(
    UUID productId,
    String productName,
    String companyName,
    String category,
    String categoryLabel,
    String imageUrl
) {}
