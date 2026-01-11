#!/bin/bash
# check-point-refund.sh
# 포인트 사용 취소 상세 확인 스크립트

echo "💰 Point Refund Detailed Check"
echo "=============================="
echo ""

# 1. 포인트 타입별 상세 통계
echo "📊 1. POINT TYPE STATISTICS"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    type as \"타입\",
    COUNT(*) as \"건수\",
    SUM(amount) as \"총 금액\",
    ROUND(AVG(amount), 2) as \"평균\",
    MIN(amount) as \"최소\",
    MAX(amount) as \"최대\"
FROM user_schema.p_point_history
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY type
ORDER BY
    CASE type
        WHEN 'EARN_CONFIRM' THEN 1
        WHEN 'EARN_PENDING' THEN 2
        WHEN 'USE_PENDING' THEN 3
        WHEN 'USE_CANCEL' THEN 4
        WHEN 'USE_CONFIRM' THEN 5
        ELSE 6
    END;
"

echo ""
echo "🔍 2. REFUND MATCHING VERIFICATION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH use_pending AS (
    SELECT
        order_id,
        user_id,
        amount,
        created_at
    FROM user_schema.p_point_history
    WHERE type = 'USE_PENDING'
      AND created_at > NOW() - INTERVAL '1 hour'
),
use_cancel AS (
    SELECT
        order_id,
        user_id,
        amount,
        created_at
    FROM user_schema.p_point_history
    WHERE type = 'USE_CANCEL'
      AND created_at > NOW() - INTERVAL '1 hour'
)
SELECT
    'Total' as \"분류\",
    COUNT(DISTINCT up.order_id) as \"USE_PENDING 주문\",
    COUNT(DISTINCT uc.order_id) as \"USE_CANCEL 주문\",
    COUNT(DISTINCT up.order_id) - COUNT(DISTINCT uc.order_id) as \"미환불 주문\"
FROM use_pending up
LEFT JOIN use_cancel uc ON up.order_id = uc.order_id;
"

echo ""
echo "👥 3. USER BALANCE CHECK (Sample 10 users)"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH latest_balance AS (
    SELECT DISTINCT ON (user_id)
        user_id,
        balance_after,
        type,
        created_at
    FROM user_schema.p_point_history
    WHERE created_at > NOW() - INTERVAL '2 hour'
    ORDER BY user_id, created_at DESC
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
echo "📈 4. BALANCE STATISTICS"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH latest_balance AS (
    SELECT DISTINCT ON (user_id)
        user_id,
        balance_after
    FROM user_schema.p_point_history
    WHERE created_at > NOW() - INTERVAL '2 hour'
    ORDER BY user_id, created_at DESC
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
echo "🔎 5. REFUND TIMELINE (Recent 20 events)"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    TO_CHAR(created_at, 'HH24:MI:SS.MS') as \"시각\",
    user_id as \"유저\",
    type as \"타입\",
    amount as \"금액\",
    balance_after as \"잔액\"
FROM user_schema.p_point_history
WHERE created_at > NOW() - INTERVAL '1 hour'
  AND type IN ('USE_PENDING', 'USE_CANCEL')
ORDER BY created_at DESC
LIMIT 20;
"

echo ""
echo "💡 6. EXPECTED vs ACTUAL"
echo ""

# 기대값 계산
CANCELLED_ORDERS=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT COUNT(*) FROM order_schema.p_order
   WHERE status = 'CANCELLED'
   AND created_at > NOW() - INTERVAL '1 hour';")

USE_PENDING_COUNT=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT COUNT(*) FROM user_schema.p_point_history
   WHERE type = 'USE_PENDING'
   AND created_at > NOW() - INTERVAL '1 hour';")

USE_CANCEL_COUNT=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT COUNT(*) FROM user_schema.p_point_history
   WHERE type = 'USE_CANCEL'
   AND created_at > NOW() - INTERVAL '1 hour';")

echo "   Expected Refunds: $CANCELLED_ORDERS (cancelled orders)"
echo "   USE_PENDING count: $USE_PENDING_COUNT"
echo "   USE_CANCEL count: $USE_CANCEL_COUNT"
echo ""

if [ "$USE_PENDING_COUNT" -eq "$USE_CANCEL_COUNT" ]; then
    echo "   ✅ REFUND STATUS: SUCCESS (All refunded)"
elif [ "$USE_CANCEL_COUNT" -gt 0 ]; then
    MISSING=$((USE_PENDING_COUNT - USE_CANCEL_COUNT))
    echo "   ⚠️  REFUND STATUS: PARTIAL ($MISSING missing)"
else
    echo "   ❌ REFUND STATUS: FAILED (No refunds)"
fi

echo ""
echo "✅ Point refund check completed!"
