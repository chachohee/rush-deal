package com.rushcrew.timedeal.domain.entity;

import com.rushcrew.common.entity.BaseEntity;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.timedeal.application.command.CreateStockCommand;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.vo.EventType;
import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.ProductItemIds;
import com.rushcrew.timedeal.domain.vo.Quantity;
import com.rushcrew.timedeal.domain.vo.StockCounts;
import com.rushcrew.timedeal.domain.vo.TimeDealProductStatus;
import com.rushcrew.timedeal.domain.vo.TimeDealStatus;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_time_deal_stock", schema = "time_deal_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class TimeDealStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_deal_product_id", nullable = false, unique = true)
    private TimeDealProduct timeDealProduct;

    @Column(name = "time_deal_id", nullable = false)
    private UUID timeDealId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "productId", column = @Column(name = "product_id", nullable = false)),
        @AttributeOverride(name = "optionId", column = @Column(name = "option_id", nullable = false))
    })
    private ProductItemIds itemIds;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "available", column = @Column(name = "available_stock", nullable = false)),
        @AttributeOverride(name = "reserved", column = @Column(name = "reserved_stock", nullable = false)),
        @AttributeOverride(name = "sold", column = @Column(name = "sold_stock", nullable = false)),
    })
    private StockCounts stockCounts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeDealStockStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

	@OneToMany(mappedBy = "timeDealStock", fetch = FetchType.LAZY,
		cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@Builder.Default
	private List<StockLog> stockLogs = new ArrayList<>();

    public static TimeDealStock create(CreateStockCommand command,
        TimeDealProduct timeDealProduct, BigDecimal originalPrice) {
        TimeDealStock stock = TimeDealStock.builder()
            .timeDealProduct(timeDealProduct)
            .timeDealId(timeDealProduct.getTimeDeal().getId())
            .itemIds(
                ProductItemIds.of(command.productId(), timeDealProduct.getItemIds().getOptionId()))
            .stockCounts(StockCounts.init(command.totalStock()))
            .status(TimeDealStockStatus.AVAILABLE)
            .originalPrice(originalPrice)
            .build();

        timeDealProduct.updateStatus(TimeDealProductStatus.IN_STOCK);

        StockLog log = StockLog.init(stock, command.totalStock());
        stock.stockLogs.add(log);

        return stock;
    }

    public void changeAvailable(Long quantity, String reason) {
        long newAvailable = this.stockCounts.getAvailable() + quantity;

        this.stockCounts = StockCounts.of(
            newAvailable,
            this.stockCounts.getReserved(),
            this.stockCounts.getSold()
        );

        if (newAvailable > 0) {
            this.timeDealProduct.updateStatus(TimeDealProductStatus.IN_STOCK);
        } else {
            this.timeDealProduct.updateStatus(TimeDealProductStatus.OUT_OF_STOCK);
        }

        StockLog log = StockLog.addLog(this, EventType.ADMIN_CONTROL, quantity, reason);
        this.stockLogs.add(log);
    }

    public void delete(Long userId) {
        if (this.stockCounts.getReserved() > 0) {
            throw new BusinessException(TimeDealErrorCode.CAN_NOT_DELETE_STOCK);
        }

        Long beforeAvailable = this.stockCounts.getAvailable();

        this.status = TimeDealStockStatus.PAUSED;
        this.stockCounts =
            StockCounts.of(0L, this.stockCounts.getReserved(), this.stockCounts.getSold());
        this.softDelete(userId);
        this.getTimeDealProduct().updateStatus(TimeDealProductStatus.OUT_OF_STOCK);

        StockLog log = StockLog.addLog(
            this, EventType.ADMIN_CONTROL, beforeAvailable,
            "재고 삭제 전 남은 재고 처리 (" + beforeAvailable + " -> 0)"
        );
        this.stockLogs.add(log);
    }

    public void reserve(Quantity quantity, OrderId orderId) {
        if (this.stockCounts.getAvailable() < quantity.getQuantity()) {
            throw new BusinessException(TimeDealErrorCode.OUT_OF_STOCK);
        }

        this.stockCounts = this.stockCounts.reserve(quantity);

        if (this.stockCounts.getAvailable() == 0) {
            this.timeDealProduct.getTimeDeal().updateStatus(TimeDealStatus.SOLD_OUT);
            this.status = TimeDealStockStatus.RESERVED;
            this.timeDealProduct.updateStatus(TimeDealProductStatus.OUT_OF_STOCK);
        }

        StockLog log = StockLog.reserve(
            this, orderId, quantity
        );
        this.stockLogs.add(log);
    }

    public void confirm(OrderId orderId, Quantity quantity) {
        this.stockCounts = this.stockCounts.confirm(quantity);

        if (this.stockCounts.getAvailable() == 0 && this.stockCounts.getReserved() == 0) {
            this.status = TimeDealStockStatus.SOLD;
        }

        StockLog log = StockLog.confirm(this, orderId, quantity);
        this.stockLogs.add(log);
    }

    public void restoreFromReserved(OrderId orderId, Quantity quantity, String reason) {
        this.stockCounts = this.stockCounts.restoreFromReserved(quantity);
        updateStatus();

        StockLog log = StockLog.restore(this, orderId, quantity, EventType.RESERVE_CANCEL, reason);
        this.stockLogs.add(log);
    }

    public void restoreFromSold(OrderId orderId, Quantity quantity, String reason) {
        this.stockCounts = this.stockCounts.restoreFromSold(quantity);
        updateStatus();

        StockLog log = StockLog.restore(this, orderId, quantity, EventType.PAYMENT_CANCEL, reason);
        this.stockLogs.add(log);
    }

    private void updateStatus() {
        this.status = TimeDealStockStatus.AVAILABLE;
        this.timeDealProduct.updateStatus(TimeDealProductStatus.IN_STOCK);
    }

    public void validQuantity(Long quantity) {
        // 변화할 재고 수량이 음수일 때, 품절인지 아닌지 체크
        if (quantity < 0 &&
            TimeDealProductStatus.OUT_OF_STOCK.equals(this.getTimeDealProduct().getStatus())) {
            throw new BusinessException(TimeDealErrorCode.CAN_NOT_DECREASE_STOCK);
        }

        // 남은 재고 수량이 감소할 수량보다 적은지 체크
        if (this.getStockCounts().getAvailable() + quantity < 0) {
            throw new BusinessException(TimeDealErrorCode.CAN_NOT_DECREASE_BELOW_ZERO);
        }
    }
}
