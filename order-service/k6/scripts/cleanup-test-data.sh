#!/bin/bash
# cleanup-test-data.sh
# 테스트 데이터 정리 + 사용자/포인트 재생성

# ✅ 스크립트 디렉토리 경로 확보
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🧹 Cleaning up test data..."
echo "=============================="
echo ""

# =====================================================
# 1. 주문 데이터 삭제
# =====================================================
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

# =====================================================
# 2. 재고 로그 초기화
# =====================================================
echo "📝 2. Cleaning STOCK LOG data..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
TRUNCATE TABLE time_deal_schema.p_stock_log RESTART IDENTITY;
" > /dev/null 2>&1
echo "   ✅ Stock log cleaned"

# =====================================================
# 3. 재고 복구
# =====================================================
echo "📈 3. Restoring STOCK data..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
UPDATE time_deal_schema.p_time_deal_stock
SET available_stock = 1000,
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
echo "   ✅ Stock restored"

# =====================================================
# 4. USER / POINT 완전 삭제 🔥
# =====================================================
echo "👤 4. Cleaning USER & POINT data..."

docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
TRUNCATE TABLE user_schema.p_point_history RESTART IDENTITY CASCADE;
TRUNCATE TABLE user_schema.p_user RESTART IDENTITY CASCADE;
" > /dev/null 2>&1

echo "   ✅ User & PointHistory fully cleaned"

# =====================================================
# 5. Redis 데이터 삭제
# =====================================================
echo "🔑 5. Cleaning REDIS data..."

docker exec rushdeal_queue_redis redis-cli FLUSHDB > /dev/null 2>&1
docker exec rushdeal_order_redis redis-cli FLUSHDB > /dev/null 2>&1
docker exec rushdeal_timedeal_redis redis-cli FLUSHDB > /dev/null 2>&1
docker exec rushdeal_user_redis redis-cli FLUSHDB > /dev/null 2>&1

echo "   ✅ All Redis cleared"

# =====================================================
# 6. Kafka 토픽 초기화
# =====================================================
echo "📨 6. Resetting KAFKA topics..."

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

for topic in "${topics[@]}"; do
  docker exec rushdeal_kafka kafka-topics \
    --bootstrap-server localhost:9092 \
    --delete \
    --topic "$topic" > /dev/null 2>&1

  docker exec rushdeal_kafka kafka-topics \
    --bootstrap-server localhost:9092 \
    --create \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 > /dev/null 2>&1
done

echo "   ✅ Kafka topics reset"


# =====================================================
# 7. 테스트 유저/포인트 재생성 🚀
# =====================================================
echo ""
echo "🧪 7. Initializing test users & points (test-data.sql)..."

# ✅ 수정된 부분
docker exec -i rushdeal_postgres \
  psql -U rushdeal -d rushdeal < "$SCRIPT_DIR/test-data.sql"

echo "   ✅ test-data.sql executed"

# =====================================================
# 8. 검증
# =====================================================
echo ""
echo "📊 Verification:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT role, COUNT(*) FROM user_schema.p_user GROUP BY role;
SELECT COUNT(*) AS users_with_points
FROM user_schema.p_point_history
WHERE type = 'EARN_CONFIRM';
"

echo ""
echo "🎉 Cleanup & Init completed!"
echo ""
echo "💡 Next steps:"
echo "   1. ./get-test-ids.sh"
echo "   2. k6 run generate-queue-tokens.js"
echo "   3. k6 run load-test-order.js"
