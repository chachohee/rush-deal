#!/bin/bash
# verify-order-creation.sh
# 주문 생성 직후 데이터 검증 (자동 취소 전)

echo "🔍 Order Creation Verification (Before Auto-Cancel)"
echo "===================================================="
echo ""

# 최근 주문 개수로 필터링
LIMIT="${1:-1000}"
echo "📊 Checking last $LIMIT orders"
echo ""

# 1. 주문 통계 (전체 + 최근)
echo "📦 1. ORDER STATISTICS (Expected: PENDING status)"
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
echo "💰 3. POINT USAGE SUMMARY"
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
AND type IN ('EARN_PENDING', 'USE_PENDING')
GROUP BY type
ORDER BY
    CASE type
        WHEN 'EARN_PENDING' THEN 1
        WHEN 'USE_PENDING' THEN 2
    END;
"

echo ""
echo "🔗 4. ORDER vs POINT VERIFICATION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH pending_orders AS (
    SELECT
        COUNT(*) as pending_count,
        COALESCE(SUM(point_used), 0) as total_point_used
    FROM (
        SELECT order_id, user_id, point_used, status
        FROM order_schema.p_order
        WHERE status = 'PENDING' AND point_used > 0
        LIMIT $LIMIT
    ) AS recent_pending
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
)
SELECT
    pending_orders.pending_count as \"PENDING 주문\",
    pending_orders.total_point_used as \"주문 포인트\",
    point_use.use_pending_count as \"USE_PENDING\",
    point_use.use_pending_amount as \"차감 금액\",
    CASE
        WHEN pending_orders.pending_count = point_use.use_pending_count
         AND pending_orders.total_point_used = point_use.use_pending_amount
        THEN '✅ MATCH'
        ELSE '❌ MISMATCH'
    END as verification
FROM pending_orders, point_use;
"

echo ""
echo "⏱️  5. ORDER CREATION RATE"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    DATE_TRUNC('minute', created_at) as minute,
    COUNT(*) as orders_per_minute,
    SUM(total_amount) as amount_per_minute
FROM (
    SELECT created_at, total_amount
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT $LIMIT
) AS recent_orders
GROUP BY DATE_TRUNC('minute', created_at)
ORDER BY minute DESC
LIMIT 10;
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
echo "⏱️  7. ORDER PROCESSING TIME"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    ROUND(MIN(EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000)::numeric, 2) as min_ms,
    ROUND(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000)::numeric, 2) as avg_ms,
    ROUND(MAX(EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000)::numeric, 2) as max_ms,
    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000)::numeric, 2) as p95_ms
FROM (
    SELECT created_at, updated_at
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT $LIMIT
) AS recent_orders
WHERE created_at IS NOT NULL AND updated_at IS NOT NULL;
"

echo ""
echo "📦 8. STOCK RESERVATION CHECK"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    id as time_deal_stock_id,
    available_stock as \"가용 재고\",
    reserved_stock as \"예약 재고\",
    sold_stock as \"판매 재고\",
    (available_stock + reserved_stock + sold_stock) as \"기존 재고\"
FROM time_deal_schema.p_time_deal_stock
WHERE reserved_stock > 0
ORDER BY id
LIMIT 10;
"

echo ""
echo "💰 9. USER BALANCE VERIFICATION"
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
echo "👥 10. SAMPLE USER BALANCES (10 users)"
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
echo "✅ Order Creation Verification Complete!"
echo ""
echo "💡 Summary:"
echo "   ✅ 주문 상태: 모두 PENDING이어야 함"
echo "   ✅ 포인트: USE_PENDING으로 차감되어야 함"
echo "   ✅ 재고: reserved_stock에 반영되어야 함"
echo "   ⏳ 다음 단계: 5분 후 자동 취소 대기"
echo ""
echo "📌 Expected State:"
echo "   - Order Status: PENDING (100%)"
echo "   - Point Type: USE_PENDING (차감 대기)"
echo "   - Stock: Reserved > 0 (예약됨)"
echo "   - User Balance: 9,000원 (10,000 - 1,000)"
