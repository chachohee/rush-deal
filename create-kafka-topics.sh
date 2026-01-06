#!/bin/bash
# create-kafka-topics.sh

echo "📋 Creating Kafka topics..."

TOPICS=(
  "stock.reserved"
  "stock.reservation_failed"
  "stock.reservation.requested"
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
    echo "  ⚠️  $topic (already exists or failed)"
  fi
done

echo ""
echo "Created topics:"
docker exec rushdeal_kafka kafka-topics --list --bootstrap-server localhost:9092
