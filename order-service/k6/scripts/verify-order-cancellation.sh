#!/bin/bash
# verify-order-cancellation.sh
# 자동 취소 후 데이터 검증 (포인트 환불 포함)

echo "🔍 Order Cancellation Verification (After Auto-Cancel)"
echo "======================================================="
echo ""

# 최근 주문 개수로 필터링
LIMIT="${1:-100}"
echo "📊 Checking last $LIMIT orders"
echo ""

# 1. 주문 통계 (전체 + 최근)
echo "📦 1. ORDER STATISTICS (Expected: CANCELLED status)"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    status,
    COUNT(*) as order_count,
    SUM(total_amount) as total_amount,
    SUM(point_used) as total_points_used
FROM order_schema.p_order
GROUP BY status
ORDER BY status;
"

echo ""
echo "📦 1-1. RECENT $LIMIT ORDERS STATISTICS"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    status,
    COUNT(*) as order_count,
    SUM(total_amount) as total_amount,
    SUM(point_used) as total_points_used
FROM (
    SELECT order_id, status, total_amount, point_used
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT $LIMIT
) AS recent_orders
GROUP BY status
ORDER BY status;
"

echo ""
echo "📊 2. ORDER ITEMS SUMMARY"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    COUNT(DISTINCT oi.order_id) as orders_with_items,
    COUNT(*) as total_items,
    SUM(oi.quantity) as total_quantity
FROM order_schema.p_order_item oi
WHERE oi.order_id IN (
    SELECT order_id
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT $LIMIT
);
"

echo ""
echo "💰 3. POINT TRANSACTION SUMMARY (Including Refunds)"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    type as \"거래 유형\",
    COUNT(*) as \"건수\",
    SUM(amount) as \"총 금액\",
    ROUND(AVG(amount), 2) as \"평균 금액\"
FROM user_schema.p_point_history ph
WHERE ph.user_id IN (
    SELECT DISTINCT user_id
    FROM order_schema.p_order
    LIMIT $LIMIT
)
AND type IN ('EARN_PENDING', 'USE_PENDING', 'USE_CANCEL')
GROUP BY type
ORDER BY
    CASE type
        WHEN 'EARN_PENDING' THEN 1
        WHEN 'USE_PENDING' THEN 2
        WHEN 'USE_CANCEL' THEN 3
    END;
"

echo ""
echo "🔗 4. ORDER vs POINT VERIFICATION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH cancelled_orders AS (
    SELECT
        COUNT(*) as cancelled_count,
        COALESCE(SUM(point_used), 0) as total_point_used
    FROM (
        SELECT order_id, user_id, point_used, status
        FROM order_schema.p_order
        WHERE status = 'CANCELLED' AND point_used > 0
        LIMIT $LIMIT
    ) AS recent_cancelled
),
point_use AS (
    SELECT
        COUNT(*) as use_pending_count,
        ABS(COALESCE(SUM(amount), 0)) as use_pending_amount
    FROM user_schema.p_point_history ph
    WHERE ph.user_id IN (
        SELECT DISTINCT user_id
        FROM order_schema.p_order
        LIMIT $LIMIT
    )
    AND type = 'USE_PENDING'
),
point_refund AS (
    SELECT
        COUNT(*) as use_cancel_count,
        COALESCE(SUM(amount), 0) as use_cancel_amount
    FROM user_schema.p_point_history ph
    WHERE ph.user_id IN (
        SELECT DISTINCT user_id
        FROM order_schema.p_order
        LIMIT $LIMIT
    )
    AND type = 'USE_CANCEL'
)
SELECT
    cancelled_orders.cancelled_count as \"취소된 주문\",
    cancelled_orders.total_point_used as \"주문 포인트\",
    point_use.use_pending_count as \"USE_PENDING\",
    point_use.use_pending_amount as \"차감 금액\",
    point_refund.use_cancel_count as \"USE_CANCEL\",
    point_refund.use_cancel_amount as \"환불 금액\",
    CASE
        WHEN point_use.use_pending_count = point_refund.use_cancel_count
         AND point_use.use_pending_amount = point_refund.use_cancel_amount
        THEN '✅ REFUND MATCH'
        WHEN point_refund.use_cancel_count = 0
        THEN '❌ NO REFUND'
        ELSE '⚠️ PARTIAL REFUND'
    END as \"환불 상태\"
FROM cancelled_orders, point_use, point_refund;
"

echo ""
echo "⏱️  5. CANCELLATION TIMING"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    CONCAT(
        FLOOR(MIN(elapsed_seconds) / 60), 'm ',
        ROUND(MIN(elapsed_seconds) % 60), 's'
    ) as \"최소 취소 시간\",
    CONCAT(
        FLOOR(AVG(elapsed_seconds) / 60), 'm ',
        ROUND(AVG(elapsed_seconds) % 60), 's'
    ) as \"평균 취소 시간\",
    CONCAT(
        FLOOR(MAX(elapsed_seconds) / 60), 'm ',
        ROUND(MAX(elapsed_seconds) % 60), 's'
    ) as \"최대 취소 시간\"
FROM (
    SELECT
        created_at,
        updated_at,
        EXTRACT(EPOCH FROM (updated_at - created_at)) as elapsed_seconds
    FROM order_schema.p_order
    WHERE status = 'CANCELLED'
    ORDER BY created_at DESC
    LIMIT $LIMIT
) AS cancelled_orders
WHERE created_at IS NOT NULL AND updated_at IS NOT NULL;
"

echo ""
echo "🎯 6. ORDER STATUS DISTRIBUTION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    status,
    COUNT(*) as count,
    ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER () * 100, 2) as percentage
FROM (
    SELECT status
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT $LIMIT
) AS recent_orders
GROUP BY status
ORDER BY count DESC;
"

echo ""
echo "📦 7. STOCK RESTORATION CHECK"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    id as time_deal_stock_id,
    available_stock as \"가용 재고\",
    reserved_stock as \"예약 재고\",
    sold_stock as \"판매 재고\",
    (available_stock + reserved_stock + sold_stock) as \"기존 재고\"
FROM time_deal_schema.p_time_deal_stock
ORDER BY id
LIMIT 10;
"

echo ""
echo "💰 8. USER BALANCE VERIFICATION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH user_list AS (
    SELECT DISTINCT user_id
    FROM order_schema.p_order
    LIMIT $LIMIT
),
latest_balance AS (
    SELECT DISTINCT ON (ph.user_id)
        ph.user_id,
        ph.balance_after,
        ph.type,
        ph.created_at
    FROM user_schema.p_point_history ph
    WHERE ph.user_id IN (SELECT user_id FROM user_list)
    ORDER BY ph.user_id, ph.created_at DESC
)
SELECT
    COUNT(*) as \"사용자 수\",
    MIN(balance_after) as \"최소 잔액\",
    MAX(balance_after) as \"최대 잔액\",
    ROUND(AVG(balance_after), 2) as \"평균 잔액\",
    SUM(balance_after) as \"총 잔액\"
FROM latest_balance;
"

echo ""
echo "👥 9. SAMPLE USER BALANCES (10 users)"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH user_list AS (
    SELECT DISTINCT user_id
    FROM order_schema.p_order
    LIMIT $LIMIT
),
latest_balance AS (
    SELECT DISTINCT ON (ph.user_id)
        ph.user_id,
        ph.balance_after,
        ph.type,
        ph.created_at
    FROM user_schema.p_point_history ph
    WHERE ph.user_id IN (SELECT user_id FROM user_list)
    ORDER BY ph.user_id, ph.created_at DESC
)
SELECT
    user_id as \"사용자 ID\",
    balance_after as \"현재 잔액\",
    type as \"마지막 거래\",
    TO_CHAR(created_at, 'HH24:MI:SS') as \"시각\"
FROM latest_balance
ORDER BY user_id
LIMIT 10;
"

echo ""
echo "🎉 10. REFUND COMPLETION CHECK"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH user_list AS (
    SELECT DISTINCT user_id
    FROM order_schema.p_order
    LIMIT $LIMIT
),
refund_stats AS (
    SELECT
        COUNT(CASE WHEN type = 'USE_PENDING' THEN 1 END) as use_pending_count,
        COUNT(CASE WHEN type = 'USE_CANCEL' THEN 1 END) as use_cancel_count,
        COUNT(CASE WHEN type = 'EARN_PENDING' THEN 1 END) as earn_pending_count,
        ABS(COALESCE(SUM(CASE WHEN type = 'USE_PENDING' THEN amount ELSE 0 END), 0)) as total_used,
        COALESCE(SUM(CASE WHEN type = 'USE_CANCEL' THEN amount ELSE 0 END), 0) as total_refunded
    FROM user_schema.p_point_history ph
    WHERE ph.user_id IN (SELECT user_id FROM user_list)
      AND type IN ('USE_PENDING', 'USE_CANCEL', 'EARN_PENDING')
)
SELECT
    CASE
        WHEN use_cancel_count > 0 AND use_cancel_count = use_pending_count
        THEN '✅ 환불 완료'
        WHEN use_cancel_count > 0 AND use_cancel_count < use_pending_count
        THEN '⚠️ 부분 환불'
        WHEN use_pending_count > 0 AND use_cancel_count = 0
        THEN '❌ 환불 미실행'
        ELSE '✅ 환불 불필요'
    END as \"환불 상태\",
    use_pending_count as \"USE_PENDING\",
    use_cancel_count as \"USE_CANCEL\",
    earn_pending_count as \"EARN_PENDING\",
    total_used as \"차감 총액\",
    total_refunded as \"환불 총액\",
    CASE
        WHEN total_used = total_refunded THEN '✅ 금액 일치'
        WHEN total_used = 0 AND total_refunded = 0 THEN '✅ 포인트 미사용'
        ELSE '❌ 금액 불일치'
    END as \"정합성\"
FROM refund_stats;
"

echo ""
echo "🔔 11. KAFKA EVENT CHECK"
echo "   ℹ️  Kafka 이벤트 발행 확인 (수동):"
echo ""
echo "   # point.refund.requested 토픽 메시지 확인"
echo "   docker exec rushdeal_kafka kafka-console-consumer \\"
echo "     --bootstrap-server localhost:9092 \\"
echo "     --topic point.refund.requested \\"
echo "     --from-beginning \\"
echo "     --max-messages 10 \\"
echo "     --timeout-ms 5000"
echo ""

echo "✅ Order Cancellation Verification Complete!"
echo ""
echo "💡 Summary:"
echo "   ✅ 주문 상태: 모두 CANCELLED이어야 함"
echo "   ✅ 포인트: USE_CANCEL로 환불되어야 함"
echo "   ✅ 재고: reserved_stock이 0으로 복구되어야 함"
echo "   ✅ 사용자 잔액: 10,000원으로 복구되어야 함"
echo ""
echo "📌 Expected State:"
echo "   - Order Status: CANCELLED (100%)"
echo "   - Point Types: USE_PENDING + USE_CANCEL (환불 완료)"
echo "   - Stock: Reserved = 0 (복구됨)"
echo "   - User Balance: 10,000원 (복구됨)"
echo "   - Refund Match: USE_PENDING count = USE_CANCEL count"
