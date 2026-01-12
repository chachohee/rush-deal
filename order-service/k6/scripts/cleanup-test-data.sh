#!/bin/bash
# cleanup-test-data.sh
# 테스트 데이터 정리 스크립트

echo "🧹 Cleaning up test data..."
echo "=============================="
echo ""

# 1. 주문 데이터 삭제
echo "📦 1. Cleaning ORDER data..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
TRUNCATE TABLE
    order_schema.p_order_item,
    order_schema.p_order_history,
    order_schema.p_order_reservation,
    order_schema.p_order,
    order_schema.p_saga_step,
    order_schema.p_saga_instance,
    order_schema.p_outbox_event
RESTART IDENTITY CASCADE;
" > /dev/null 2>&1
echo "   ✅ Order data cleaned"

# 2. 재고 로그 초기화
echo "📝 2. Cleaning STOCK LOG data..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
TRUNCATE TABLE time_deal_schema.p_stock_log RESTART IDENTITY;
" > /dev/null 2>&1
echo "   ✅ Stock log cleaned"

# 3. 재고 복구
echo "📈 3. Restoring STOCK data..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
UPDATE time_deal_schema.p_time_deal_stock
SET available_stock = 100,
    reserved_stock = 0,
    sold_stock = 0,
    version = 0,
    updated_at = NOW()
WHERE time_deal_product_id IN (
    SELECT id FROM time_deal_schema.p_time_deal_product
    WHERE time_deal_id IN (
        SELECT id FROM time_deal_schema.p_time_deal
        WHERE title LIKE '%나이키%'
    )
);
" > /dev/null 2>&1
echo "   ✅ Stock restored to 400 units"

# 4. 포인트 이력 정리 (초기 적립 제외)
echo "💰 4. Cleaning POINT history (keeping initial balance)..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
DELETE FROM user_schema.p_point_history
WHERE type != 'EARN_CONFIRM';
" > /dev/null 2>&1
echo "   ✅ Point history cleaned (EARN_CONFIRM preserved)"

# 5. Redis 데이터 삭제
echo "🔑 5. Cleaning REDIS data..."

# 5-1. 큐 Redis
echo "   🔹 Cleaning Queue Redis..."
docker exec rushdeal_queue_redis redis-cli FLUSHDB > /dev/null 2>&1
sleep 1
QUEUE_KEYS=$(docker exec rushdeal_queue_redis redis-cli DBSIZE | grep -oE '[0-9]+')
if [ "$QUEUE_KEYS" -le 2 ]; then
    echo "      ✅ Queue Redis cleared ($QUEUE_KEYS keys - scheduler tokens only)"
else
    echo "      ⚠️  Warning: $QUEUE_KEYS keys remaining (expected ≤ 2)"
fi

# 5-2. 주문 Redis
echo "   🔹 Cleaning Order Redis..."
docker exec rushdeal_order_redis redis-cli FLUSHDB > /dev/null 2>&1
sleep 1
ORDER_KEYS=$(docker exec rushdeal_order_redis redis-cli DBSIZE | grep -oE '[0-9]+')
echo "      ✅ Order Redis cleared ($ORDER_KEYS keys)"

# 5-3. 타임딜 Redis
echo "   🔹 Cleaning TimeDeal Redis..."
docker exec rushdeal_timedeal_redis redis-cli FLUSHDB > /dev/null 2>&1
sleep 1
TIMEDEAL_KEYS=$(docker exec rushdeal_timedeal_redis redis-cli DBSIZE | grep -oE '[0-9]+')
echo "      ✅ TimeDeal Redis cleared ($TIMEDEAL_KEYS keys)"

# 5-4. 유저 Redis
echo "   🔹 Cleaning User Redis..."
docker exec rushdeal_user_redis redis-cli FLUSHDB > /dev/null 2>&1
sleep 1
USER_KEYS=$(docker exec rushdeal_user_redis redis-cli DBSIZE | grep -oE '[0-9]+')
echo "      ✅ User Redis cleared ($USER_KEYS keys)"

# 6. Kafka 토픽 초기화 (선택사항)
echo "📨 6. Resetting KAFKA topics..."

# 토픽 목록
topics=(
  "stock.reserved"
  "stock.reservation.failed"
  "stock.reservation.requested"
  "stock.restore.requested"
  "stock.restore.failed"
  "order.created"
  "order.cancelled"
  "point.use.cancel.requested"
  "order-complete-token-remove"
)

# 각 토픽 삭제 및 재생성
for topic in "${topics[@]}"; do
  # 토픽 삭제
  docker exec rushdeal_kafka kafka-topics \
    --bootstrap-server localhost:9092 \
    --delete \
    --topic "$topic" > /dev/null 2>&1

  # 토픽 재생성
  docker exec rushdeal_kafka kafka-topics \
    --bootstrap-server localhost:9092 \
    --create \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 > /dev/null 2>&1
done
echo "   ✅ Kafka topics reset"

echo ""
echo "✅ Cleanup completed!"
echo ""
echo "📊 Verification:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT
    '주문' as table_name,
    COUNT(*) as count
FROM order_schema.p_order
UNION ALL
SELECT
    '재고 로그',
    COUNT(*)
FROM time_deal_schema.p_stock_log
UNION ALL
SELECT
    '포인트 (초기 적립)',
    COUNT(*)
FROM user_schema.p_point_history
WHERE type = 'EARN_CONFIRM'
UNION ALL
SELECT
    '재고 (총)',
    SUM(available_stock)
FROM time_deal_schema.p_time_deal_stock
UNION ALL
SELECT
    '재고 (예약)',
    SUM(reserved_stock)
FROM time_deal_schema.p_time_deal_stock;
"

echo ""
echo "💡 Next steps:"
echo "   1. Run: ./get-test-ids.sh"
echo "   2. Update load-test-order.js with new IDs"
echo "   3. Run: k6 run generate-queue-tokens.js"
echo "   4. Run: k6 run load-test-order.js"
