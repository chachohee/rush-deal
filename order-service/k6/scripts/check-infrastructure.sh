#!/bin/bash
# check-infrastructure.sh

echo "🔍 Checking infrastructure..."
echo ""

# 실패 카운터
FAILED=0

# PostgreSQL
echo -n "PostgreSQL: "
if docker exec rushdeal_postgres pg_isready -U rushdeal -d rushdeal > /dev/null 2>&1; then
    echo "✅"
else
    echo "❌"
    FAILED=$((FAILED + 1))
fi

# Redis instances
for name in auth user queue order timedeal gateway; do
  echo -n "Redis ($name): "
  if docker exec rushdeal_${name}_redis redis-cli ping > /dev/null 2>&1; then
      echo "✅"
  else
      echo "❌"
      # 필수 Redis만 실패 카운트 (queue, order, timedeal)
      if [ "$name" = "queue" ] || [ "$name" = "order" ] || [ "$name" = "timedeal" ]; then
          FAILED=$((FAILED + 1))
      fi
  fi
done

# Kafka
echo -n "Kafka: "
if docker exec rushdeal_kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "✅"
else
    echo "❌"
    FAILED=$((FAILED + 1))
fi

# Prometheus (optional)
echo -n "Prometheus: "
if docker exec rushdeal_prometheus wget -q --spider http://localhost:9090/-/healthy > /dev/null 2>&1; then
    echo "✅"
else
    echo "⚠️  (optional)"
fi

# Grafana (optional)
echo -n "Grafana: "
if docker exec rushdeal_grafana wget -q --spider http://localhost:3000/api/health > /dev/null 2>&1; then
    echo "✅"
else
    echo "⚠️  (optional)"
fi

echo ""

# 결과 반환
if [ $FAILED -eq 0 ]; then
    echo "✅ All required infrastructure is ready!"
    exit 0
else
    echo "❌ $FAILED required component(s) failed!"
    echo "   Required: PostgreSQL, Kafka, Queue Redis, Order Redis"
    exit 1
fi
