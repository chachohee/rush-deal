package com.rushcrew.order_service.infrastructure.persistence.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.query.dto.OrderDetailDto;
import com.rushcrew.order_service.application.query.dto.OrderItemQueryDto;
import com.rushcrew.order_service.application.query.dto.OrderListDto;
import com.rushcrew.order_service.application.query.dto.OrderSearchCriteria;
import com.rushcrew.order_service.application.port.out.OrderQueryPort;
import com.rushcrew.order_service.domain.vo.ShippingInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryAdapter implements OrderQueryPort {

	private final EntityManager entityManager;

	@Override
	public Optional<OrderDetailDto> findOrderDetail(UUID orderId) {
		String sql = """
			SELECT
			    o.order_id,
			    o.user_id,
			    o.status,
			    o.total_amount,
			    o.point_used,
			    o.final_amount,
			    o.ordered_at,
			    o.payment_completed_at,
			    o.purchase_confirmed_at,
			    o.cancelled_at,
			    o.auto_confirm_scheduled_at,
			    o.recipient_name,
			    o.recipient_phone,
			    o.zip_code,
			    o.address_base,
			    o.address_detail,
			    o.delivery_message,
			    oi.order_item_id,
			    oi.quantity,
			    oi.unit_price,
			    oi.discount_price,
			    oi.subtotal,
			    oi.product_snapshot->>'productName' AS product_name,
			    oi.product_snapshot->>'optionName'  AS option_name
			FROM order_schema.p_order o
			LEFT JOIN order_schema.p_order_item oi ON o.order_id = oi.order_id
			WHERE o.order_id = :orderId
			ORDER BY oi.created_at
			""";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("orderId", orderId);

		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();

		if (rows.isEmpty()) {
			return Optional.empty();
		}

		Object[] first = rows.get(0);

		ShippingInfo shippingInfo = ShippingInfo.builder()
			.recipientName((String) first[11])
			.recipientPhone((String) first[12])
			.zipCode((String) first[13])
			.addressBase((String) first[14])
			.addressDetail((String) first[15])
			.deliveryMessage((String) first[16])
			.build();

		List<OrderItemQueryDto> orderItems = rows.stream()
			.filter(row -> row[17] != null)
			.map(row -> OrderItemQueryDto.builder()
				.orderItemId(UUID.fromString(row[17].toString()))
				.productName((String) row[22])
				.optionName((String) row[23])
				.quantity(((Number) row[18]).longValue())
				.unitPrice(row[19] != null ? (BigDecimal) row[19] : null)
				.discountPrice(row[20] != null ? (BigDecimal) row[20] : BigDecimal.ZERO)
				.subtotal(row[21] != null ? (BigDecimal) row[21] : BigDecimal.ZERO)
				.build())
			.toList();

		OrderDetailDto result = OrderDetailDto.builder()
			.orderId(UUID.fromString(first[0].toString()))
			.userId(((Number) first[1]).longValue())
			.orderStatus((String) first[2])
			.totalAmount((BigDecimal) first[3])
			.pointUsed(((Number) first[4]).longValue())
			.finalAmount((BigDecimal) first[5])
			.orderedAt((Instant) first[6])
			.paymentCompletedAt(first[7] != null ? (Instant) first[7] : null)
			.purchaseConfirmedAt(first[8] != null ? (Instant) first[8] : null)
			.cancelledAt(first[9] != null ? (Instant) first[9] : null)
			.autoConfirmScheduledAt(first[10] != null ? (Instant) first[10] : null)
			.shippingInfo(shippingInfo)
			.orderItems(orderItems)
			.build();

		return Optional.of(result);
	}

	@Override
	public Page<OrderListDto> findByCriteria(
		OrderSearchCriteria criteria,
		Pageable pageable
	) {
		// Count 쿼리
		String countSql = """
            SELECT COUNT(DISTINCT o.order_id)
            FROM order_schema.p_order o
            WHERE o.user_id = :userId
            """;

		Query countQuery = entityManager.createNativeQuery(countSql);
		countQuery.setParameter("userId", criteria.getUserId());
		Long total = ((Number) countQuery.getSingleResult()).longValue();

		// 데이터 조회 쿼리
		String sql = """
            SELECT 
                o.order_id,
                o.status,
                o.final_amount,
                o.ordered_at,
                COUNT(oi.order_item_id)::int as item_count,
                MIN(oi.product_snapshot->>'productName') as first_product_name
            FROM order_schema.p_order o
            LEFT JOIN order_schema.p_order_item oi ON o.order_id = oi.order_id
            WHERE o.user_id = :userId
            GROUP BY o.order_id, o.status, o.final_amount, o.ordered_at
            ORDER BY o.ordered_at DESC
            LIMIT :limit OFFSET :offset
            """;

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("userId", criteria.getUserId());
		query.setParameter("limit", pageable.getPageSize());
		query.setParameter("offset", pageable.getOffset());

		@SuppressWarnings("unchecked")
		List<Object[]> results = query.getResultList();

		List<OrderListDto> content = results.stream()
			.map(row -> OrderListDto.builder()
				.orderId(UUID.fromString(row[0].toString()))
				.orderStatus((String) row[1])
				.finalAmount((BigDecimal) row[2])
				.orderedAt(((Instant) row[3]))
				.itemCount((Integer) row[4])
				.firstProductName((String) row[5])
				.build())
			.toList();

		return new PageImpl<>(content, pageable, total);
	}
}
