package com.rushcrew.user_service.point.domain.entity;

import com.rushcrew.user_service.point.domain.enums.PointType;
import com.rushcrew.user_service.point.domain.vo.OrderId;
import com.rushcrew.user_service.point.domain.vo.Point;
import com.rushcrew.user_service.point.domain.vo.SagaId;
import com.rushcrew.user_service.point.domain.vo.UserId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "p_point_history", schema = "user_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @AttributeOverride(name = "id", column = @Column(name = "user_id"))
    private UserId userId;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "order_id"))
    private OrderId orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount"))
    private Point amount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "balance_after"))
    private Point balanceAfter;

    @Enumerated(EnumType.STRING)
    private PointType type;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "saga_id"))
    private SagaId sagaId;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public static PointHistory createPendingEarn(
        UserId userId,
        OrderId orderId,
        Point amount,
        Point currentBalance,
        SagaId sagaId
    ) {
        return create(
            userId,
            orderId,
            amount,
            currentBalance,
            PointType.EARN_PENDING,
            sagaId
        );
    }

    public static PointHistory createPendingUse(
        UserId userId,
        OrderId orderId,
        Point amount,
        Point currentBalance,
        SagaId sagaId
    ) {
        Point updatedBalance = currentBalance.subtract(amount.getAmount());

        return create(
            userId,
            orderId,
            amount,
            updatedBalance,
            PointType.USE_PENDING,
            sagaId
        );
    }

    public static PointHistory createEarnConfirm(
        UserId userId,
        OrderId orderId,
        Point amount,
        Point balanceAfter,
        LocalDateTime createdAt,
        SagaId sagaId
    ) {
        PointHistory history = create(
            userId,
            orderId,
            amount,
            balanceAfter,
            PointType.EARN_CONFIRM,
            sagaId
        );
        history.createdAt = createdAt;
        return history;
    }

    public static PointHistory confirmEarn(
        UserId userId,
        OrderId orderId,
        Point amount,
        Point balanceAfter,
        SagaId sagaId
    ) {
        return create(
            userId,
            orderId,
            amount,
            balanceAfter,
            PointType.EARN_CONFIRM,
            sagaId
        );
    }

    public boolean isOwnedBy(UserId userId) {
        return this.userId.equals(userId);
    }

    public Optional<PointHistory> cancelIfPossible(Point currentBalance) {
        LocalDateTime cancelTime = LocalDateTime.now();
        if (this.type.isEarnCancellable()) {
            this.confirmedAt = cancelTime;
            return Optional.of(toEarnCancelHistory(currentBalance));
        }
        if (this.type.isUseCancellable()) {
            this.confirmedAt = cancelTime;
            return Optional.of(toUseCancelHistory(currentBalance));
        }
        return Optional.empty();
    }

    public boolean isCanceled() {
        return this.type.isCanceledStatus(); // Enum 호출
    }

    public boolean hasSagaId(SagaId sagaId) {
        return this.sagaId.equals(sagaId);
    }


    public boolean isEarnConfirmed() {
        return this.type.isConfirmedStatus(); // Enum 호출
    }

    private static PointHistory create(
        UserId userId,
        OrderId orderId,
        Point amount,
        Point balanceAfter,
        PointType type,
        SagaId sagaId
    ) {
        PointHistory history = new PointHistory();
        history.userId = userId;
        history.orderId = orderId;
        history.amount = amount;
        history.balanceAfter = balanceAfter;
        history.type = type;
        history.sagaId = sagaId;
        return history;
    }

    private PointHistory toEarnCancelHistory(Point currentBalance) {
        return create(
            this.userId,
            this.orderId,
            this.amount,
            currentBalance,
            PointType.EARN_CANCEL,
            sagaId
        );
    }

    private PointHistory toUseCancelHistory(Point currentBalance) {
        Point updatedBalance = currentBalance.add(this.amount.getAmount());
        return create(
            this.userId,
            this.orderId,
            this.amount,
            updatedBalance,
            PointType.USE_CANCEL,
            sagaId
        );
    }

	// PointHistory.java에 추가할 메서드

	/**
	 * 포인트 환불 이력 생성 (USE_PENDING → REFUND 처리)
	 *
	 * @param userId 사용자 ID
	 * @param orderId 주문 ID
	 * @param amount 환불 포인트 (양수)
	 * @param balanceAfter 환불 후 잔액
	 * @param createdAt 생성 시각
	 * @param sagaId Saga ID
	 * @return 환불 확정 이력
	 */
	public static PointHistory createRefundConfirm(
		UserId userId,
		OrderId orderId,
		Point amount,
		Point balanceAfter,
		LocalDateTime createdAt,
		SagaId sagaId
	) {
		// USE_CANCEL 타입 사용 (기존 enum 활용)
		PointHistory history = create(
			userId,
			orderId,
			amount,          // 양수 값
			balanceAfter,    // 환불 후 증가한 잔액
			PointType.USE_CANCEL,  // 사용 취소 = 환불
			sagaId
		);
		history.createdAt = createdAt;
		history.confirmedAt = createdAt;  // 환불은 즉시 확정
		return history;
	}
}
