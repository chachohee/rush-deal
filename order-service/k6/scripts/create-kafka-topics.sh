#!/bin/bash
# create-kafka-topics.sh
# "주문 생성 ~ 5분 내 미결제 시 주문 자동 취소" 흐름에 관련된 토픽들

echo "📋 Creating Kafka topics..."

TOPICS=(
  # Stock
  "stock.reserved"
  "stock.reservation.failed"
  "stock.reservation.requested"
  "stock.restore.requested"
  "stock.restore.failed"

  # Order
  "order.created"
  "order.updated"
  "order.cancelled"
  "order.paid"
  "order.purchase.confirmed"
  "order.refunded"

  # Point
  "point.earn.requested"
  "point.use.cancel.requested"
  "point.refund.requested"

  # Queue
  "order-complete-token-remove"
)

for topic in "${TOPICS[@]}"; do
  docker exec rushdeal_kafka kafka-topics --create \
    --topic "$topic" \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists 2>/dev/null

  if [ $? -eq 0 ]; then
    echo "  ✅ $topic"
  else
    echo "  ⚠️  $topic (already exists)"
  fi
done

echo ""
echo "📊 Created topics:"
docker exec rushdeal_kafka kafka-topics --list --bootstrap-server localhost:9092 | sort

echo ""
echo "✅ Kafka topics creation completed!"
