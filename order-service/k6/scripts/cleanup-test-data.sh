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
    order_schema.p_saga_step,
    order_schema.p_saga_instance,
    order_schema.p_order
RESTART IDENTITY CASCADE;
" > /dev/null 2>&1
echo "   ✅ Order data cleaned"

# 2. 재고 복구
echo "📈 2. Restoring STOCK data..."
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

# 3. 포인트 이력 정리 (초기 적립 제외)
echo "💰 3. Cleaning POINT history (keeping initial balance)..."
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
DELETE FROM user_schema.p_point_history
WHERE type != 'EARN_CONFIRM'
   OR created_at > NOW() - INTERVAL '1 hour';
" > /dev/null 2>&1
echo "   ✅ Point history cleaned"

# 4. Redis 큐 토큰 삭제
echo "🔑 4. Cleaning REDIS queue tokens..."

# 현재 DB의 모든 키 삭제
docker exec rushdeal_queue_redis redis-cli FLUSHDB > /dev/null 2>&1

# Redis가 완전히 정리될 때까지 대기
sleep 2

# 검증: Redis 키 개수 확인
REDIS_KEYS=$(docker exec rushdeal_queue_redis redis-cli DBSIZE | grep -oE '[0-9]+')
if [ "$REDIS_KEYS" -le 2 ]; then
    echo "   ✅ Queue tokens cleared ($REDIS_KEYS keys - scheduler tokens only)"
else
    echo "   ⚠️  Warning: $REDIS_KEYS keys remaining (expected ≤ 2)"
fi

# 5. Kafka 토픽 초기화 (선택사항)
echo "📨 5. Resetting KAFKA topics..."

# 토픽 목록
topics=(
  "stock.reserved"
  "stock.reservation.failed"
  "stock.reservation.requested"
  "stock.reservation.cancelled"
  "order.created"
  "order.paid"
  "order.cancelled"
  "order.updated"
  "order.refunded"
  "order.purchase.confirmed"
  "payment.completed"
  "payment.cancelled"
  "payment.refund.requested"
  "point.earn.requested"
  "point.refund.requested"
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
