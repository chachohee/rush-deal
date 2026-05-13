package com.rushcrew.timedeal.search.dto;

import com.rushcrew.timedeal.search.document.TimeDealDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SearchHitItem(
    String id,
    String title,
    String description,
    String productName,
    String companyName,
    String category,
    String status,
    Long price,
    String imageUrl,
    Instant startAt,
    Instant endAt,
    Map<String, List<String>> highlights
) {

    public static SearchHitItem from(TimeDealDocument doc, Map<String, List<String>> highlights) {
        return new SearchHitItem(
            doc.getId(),
            doc.getTitle(),
            doc.getDescription(),
            doc.getProductName(),
            doc.getCompanyName(),
            doc.getCategory(),
            doc.getStatus(),
            doc.getPrice(),
            doc.getImageUrl(),
            doc.getStartAt(),
            doc.getEndAt(),
            highlights
        );
    }
}
