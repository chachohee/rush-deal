package com.rushcrew.timedeal.domain.entity;

import com.rushcrew.common.entity.BaseEntity;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.vo.EventType;
import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.Quantity;
import jakarta.persistence.AttributeOverride;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_stock_log", schema = "time_deal_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class StockLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_deal_stock_id", nullable = false)
    private TimeDealStock timeDealStock;

    @Embedded
    @AttributeOverride(name = "id", column = @Column(name = "order_id"))
    private OrderId orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Embedded
    @AttributeOverride(name = "count", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    @Column(columnDefinition = "TEXT")
    private String description;

    public static StockLog init(TimeDealStock timeDealStock, Long quantity) {
        return StockLog.builder()
            .timeDealStock(timeDealStock)
            .eventType(EventType.INIT)
            .quantity(Quantity.of(quantity))
            .build();
    }

    public static StockLog addLog(
        TimeDealStock timeDealStock, EventType eventType, Long quantity, String description
    ) {
        return StockLog.builder()
            .timeDealStock(timeDealStock)
            .eventType(eventType)
            .quantity(Quantity.of(quantity))
            .description(description)
            .build();
    }

    public static StockLog reserve(TimeDealStock stock, OrderId orderId, Quantity quantity) {
        return StockLog.builder()
            .timeDealStock(stock)
            .orderId(orderId)
            .eventType(EventType.RESERVE)
            .description("주문")
            .quantity(quantity)
            .build();
    }

    public static StockLog confirm(
        TimeDealStock stock, OrderId orderId, Quantity quantity
    ) {
        return StockLog.builder()
            .timeDealStock(stock)
            .orderId(orderId)
            .eventType(EventType.SELL)
            .description("결제")
            .quantity(quantity)
            .build();
    }

    public static StockLog restore(
        TimeDealStock stock, OrderId orderId, Quantity quantity, EventType eventType,
        String description
    ) {
        return StockLog.builder()
            .timeDealStock(stock)
            .orderId(orderId)
            .eventType(eventType)
            .description(description)
            .quantity(quantity)
            .build();
      
    }
  
    public void validateOrderQuantity(Quantity quantity) {
        if (!Objects.equals(this.getQuantity().getQuantity(), quantity.getQuantity())) {
            throw new BusinessException(TimeDealErrorCode.INVALID_ORDER_INFO);
        }
    }
}
