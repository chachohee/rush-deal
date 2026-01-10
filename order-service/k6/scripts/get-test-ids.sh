#!/bin/bash
# get-test-ids.sh

echo "📋 Test Data IDs"
echo "================"
echo ""

# -----------------------------------------------------
# 1) PRODUCT ID 조회
# -----------------------------------------------------
echo "🛍️ Product ID:"
PRODUCT_ID=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -c \
  "SELECT id FROM product_schema.p_product WHERE product_name LIKE '%후드집업%' LIMIT 1;" | xargs)

echo "$PRODUCT_ID"


# -----------------------------------------------------
# 2) TIME DEAL ID 조회
# -----------------------------------------------------
echo ""
echo "⏰ TimeDeal ID:"
TIMEDEAL_ID=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -c \
  "SELECT id FROM time_deal_schema.p_time_deal WHERE title LIKE '%나이키%' LIMIT 1;" | xargs)

echo "$TIMEDEAL_ID"


# -----------------------------------------------------
# 3) TIME DEAL PRODUCT 조회 (time_deal_id 기반)
# -----------------------------------------------------
echo ""
echo "📦 TimeDeal Product IDs:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT id FROM time_deal_schema.p_time_deal_product WHERE time_deal_id = '$TIMEDEAL_ID' ORDER BY created_at;"


# -----------------------------------------------------
# 4) TIME DEAL STOCK 조회
# -----------------------------------------------------
echo ""
echo "📦 Stock IDs:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT s.id
   FROM time_deal_schema.p_time_deal_stock s
   JOIN time_deal_schema.p_time_deal_product tdp ON s.time_deal_product_id = tdp.id
   WHERE tdp.time_deal_id = '$TIMEDEAL_ID'
   ORDER BY s.created_at;"

echo ""
echo "✅ Done!"
