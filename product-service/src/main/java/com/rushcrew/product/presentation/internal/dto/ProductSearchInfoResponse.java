package com.rushcrew.product.presentation.internal.dto;

import java.util.UUID;

public record ProductSearchInfoResponse(
    UUID productId,
    String productName,
    String companyName,
    String category
) {}
