package com.rushcrew.timedeal.interest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "p_interested_deal",
    schema = "time_deal_schema",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_interested_deal_user_deal",
        columnNames = {"user_id", "time_deal_id"}
    ),
    indexes = {
        @Index(name = "idx_interested_deal_time_deal", columnList = "time_deal_id"),
        @Index(name = "idx_interested_deal_user", columnList = "user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class InterestedDeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "time_deal_id", nullable = false)
    private UUID timeDealId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static InterestedDeal create(Long userId, UUID timeDealId) {
        return InterestedDeal.builder()
            .userId(userId)
            .timeDealId(timeDealId)
            .createdAt(Instant.now())
            .build();
    }
}
