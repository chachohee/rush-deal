#!/bin/bash
# check-infrastructure.sh

echo "🔍 Checking infrastructure..."

# PostgreSQL
echo -n "PostgreSQL: "
docker exec rushdeal_postgres pg_isready -U rushdeal -d rushdeal && echo "✅" || echo "❌"

# Redis instances
for name in auth user queue order timedeal gateway; do
  echo -n "Redis ($name): "
  docker exec rushdeal_${name}_redis redis-cli ping && echo "✅" || echo "❌"
done

# Kafka
echo -n "Kafka: "
docker exec rushdeal_kafka kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null && echo "✅" || echo "❌"

# Prometheus
echo -n "Prometheus: "
curl -sf http://localhost:9090/-/healthy &>/dev/null && echo "✅" || echo "❌"

# Grafana
echo -n "Grafana: "
curl -sf http://localhost:3000/api/health &>/dev/null && echo "✅" || echo "❌"
