#!/bin/bash
# create-kafka-topics.sh

echo "📋 Creating Kafka topics..."

TOPICS=(
  # Stock 관련
  "stock.reserved"
  "stock.reservation.failed"
  "stock.reservation.requested"
  "stock.reservation.cancelled"

  # Order 관련
  "order.created"
  "order.paid"
  "order.cancelled"
  "order.updated"
  "order.refunded"
  "order.purchase.confirmed"

  # Payment 관련
  "payment.completed"
  "payment.cancelled"
  "payment.refund.requested"

  # Point 관련
  "point.earn.requested"
  "point.refund.requested"

  # Queue 관련
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
