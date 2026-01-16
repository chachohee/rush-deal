#!/bin/bash
# run-order-flow-validation-test.sh
# Saga 통합 검증 테스트 자동 실행 스크립트 (IP 자동 감지)

set -e  # 에러 발생 시 즉시 중단

# 🔥 작업 디렉토리 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_DIR="$(dirname "$SCRIPT_DIR")"
TESTS_DIR="$K6_DIR/tests"
OUTPUTS_DIR="$K6_DIR/outputs"

# outputs 디렉토리 생성
mkdir -p "$OUTPUTS_DIR"

# 작업 디렉토리를 k6 디렉토리로 변경 (스크립트 실행 편의성)
cd "$K6_DIR"

echo "🚀 RushDeal Load Test - Full Automation"
echo "========================================"
echo ""
echo "📁 Working Directory: $K6_DIR"
echo "📁 Scripts Directory: $SCRIPT_DIR"
echo "📁 Tests Directory: $TESTS_DIR"
echo "📁 Outputs Directory: $OUTPUTS_DIR"
echo ""

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 재고 스냅샷 저장 함수 (가독성 개선)
# Note: 이 함수는 사용하지 않습니다 (단일 파일로 통합)
save_stock_snapshot() {
    local output_file=$1
    local title=$2

    {
        echo "========================================"
        echo "$title"
        echo "========================================"
        echo ""
        printf "%-38s | %9s | %9s | %9s | %9s\n" "Stock ID" "Available" "Reserved" "Sold" "Total"
        echo "----------------------------------------|-----------|-----------|-----------|----------"

        docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c "
            SELECT
                id,
                available_stock,
                reserved_stock,
                sold_stock,
                (available_stock + reserved_stock + sold_stock) as total
            FROM time_deal_schema.p_time_deal_stock
            ORDER BY id;
        " | while IFS='|' read -r id avail resv sold total; do
            printf "%-38s | %9s | %9s | %9s | %9s\n" "$id" "$avail" "$resv" "$sold" "$total"
        done

        echo ""
        echo "Captured at: $(date '+%Y-%m-%d %H:%M:%S')"
        echo ""
    } > "$output_file"
}

# IP 주소 자동 감지 함수
get_wsl_ip() {
    # 방법 1: cmd.exe 사용 (가장 안정적)
    local wsl_ip=$(cmd.exe /c "ipconfig | findstr IPv4" 2>/dev/null | \
        tr -d '\r' | \
        grep -oE '172\.[0-9]+\.[0-9]+\.[0-9]+' | \
        head -1)

    # 방법 2: PowerShell 사용 (방법 1 실패 시)
    if [ -z "$wsl_ip" ]; then
        wsl_ip=$(powershell.exe -Command "ipconfig" 2>/dev/null | \
            tr -d '\r' | \
            grep -a -oE '172\.[0-9]+\.[0-9]+\.[0-9]+' | \
            head -1)
    fi

    # 방법 3: hostname -I 사용 (WSL2 자체 IP)
    if [ -z "$wsl_ip" ]; then
        wsl_ip=$(hostname -I | grep -oE '172\.[0-9]+\.[0-9]+\.[0-9]+' | head -1)
    fi

    echo "$wsl_ip"
}

# IP 주소 감지 및 설정
log_info "Detecting WSL IP address..."
WSL_IP=$(get_wsl_ip)

if [ -z "$WSL_IP" ]; then
    log_error "Failed to detect WSL IP address"
    log_warning "Please manually check your IP with: ipconfig | findstr IPv4"
    read -p "Enter your WSL IP address (e.g., 172.30.1.72): " WSL_IP
fi

# 서비스 IP 주소는 WSL IP와 동일 (Docker 컨테이너가 같은 네트워크)
QUEUE_SERVICE_IP="$WSL_IP"
ORDER_SERVICE_IP="$WSL_IP"

log_success "WSL IP detected: $WSL_IP"
log_success "Services will use: $WSL_IP (Queue:8040, Order:8050)"
echo ""

# Step 1: 인프라 확인
log_info "Step 1: Checking infrastructure..."
chmod +x "$SCRIPT_DIR/check-infrastructure.sh"
"$SCRIPT_DIR/check-infrastructure.sh"

# Step 2: 기존 데이터 정리 (선택사항)
read -p "🧹 Clean existing test data? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "Step 2: Cleaning up old test data..."
    chmod +x "$SCRIPT_DIR/cleanup-test-data.sh"
    "$SCRIPT_DIR/cleanup-test-data.sh"
    log_success "Cleanup completed"
else
    log_warning "Skipping cleanup"
fi
echo ""

# Step 3: Kafka 토픽 생성
log_info "Step 3: Creating Kafka topics..."
chmod +x "$SCRIPT_DIR/create-kafka-topics.sh"
"$SCRIPT_DIR/create-kafka-topics.sh" > /dev/null 2>&1
log_success "Kafka topics created"
echo ""

# Step 4: 테스트 ID 조회
log_info "Step 4: Getting test IDs..."
chmod +x "$SCRIPT_DIR/get-test-ids.sh"
"$SCRIPT_DIR/get-test-ids.sh" > "$OUTPUTS_DIR/test-ids.txt"

PRODUCT_ID=$(grep "Product ID:" "$OUTPUTS_DIR/test-ids.txt" -A 1 | tail -1 | xargs)
TIMEDEAL_ID=$(grep "TimeDeal ID:" "$OUTPUTS_DIR/test-ids.txt" -A 1 | tail -1 | xargs)

if [ -z "$PRODUCT_ID" ] || [ -z "$TIMEDEAL_ID" ]; then
    log_error "Failed to get Product ID or TimeDeal ID"
    log_warning "Please create product and timedeal manually"
    exit 1
fi

log_success "Product ID: $PRODUCT_ID"
log_success "TimeDeal ID: $TIMEDEAL_ID"
echo ""

# Step 5: Stock IDs 조회
log_info "Step 5: Getting Stock IDs..."
STOCK_IDS=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT s.id FROM time_deal_schema.p_time_deal_stock s
   JOIN time_deal_schema.p_time_deal_product tdp ON s.time_deal_product_id = tdp.id
   WHERE tdp.time_deal_id = '$TIMEDEAL_ID'
   ORDER BY s.created_at;" | tr '\n' ',' | sed 's/,$//')

if [ -z "$STOCK_IDS" ]; then
    log_error "Failed to get Stock IDs"
    exit 1
fi

IFS=',' read -ra STOCK_ARRAY <<< "$STOCK_IDS"
if [ ${#STOCK_ARRAY[@]} -ne 4 ]; then
    log_error "Expected 4 stock IDs, got ${#STOCK_ARRAY[@]}"
    exit 1
fi

log_success "Stock IDs retrieved: ${#STOCK_ARRAY[@]} items"
echo ""

# Step 6: 큐 토큰 발급
log_info "Step 6: Generating queue tokens..."
if [ ! -f "$TESTS_DIR/generate-queue-tokens.js" ]; then
    log_error "generate-queue-tokens.js not found at $TESTS_DIR!"
    exit 1
fi

# Queue Service URL 설정 (감지된 WSL IP 우선)
QUEUE_SERVICE_URL="http://$QUEUE_SERVICE_IP:8040"

log_info "Updating generate-queue-tokens.js with URL: $QUEUE_SERVICE_URL"
sed -i "s/const PRODUCT_ID = .*/const PRODUCT_ID = '$PRODUCT_ID';/" "$TESTS_DIR/generate-queue-tokens.js"
sed -i "s|const QUEUE_SERVICE = .*|const QUEUE_SERVICE = '$QUEUE_SERVICE_URL';|" "$TESTS_DIR/generate-queue-tokens.js"

# 헬스체크로 서버 연결 확인
log_info "Checking Queue Service connectivity..."
if curl -s -f "$QUEUE_SERVICE_URL/actuator/health" > /dev/null 2>&1; then
    log_success "Queue Service is ready at $QUEUE_SERVICE_URL"
else
    log_warning "Queue Service not reachable at $QUEUE_SERVICE_URL"

    # localhost로 폴백 시도
    log_info "Trying localhost:8040 as fallback..."
    QUEUE_SERVICE_URL="http://localhost:8040"
    sed -i "s|const QUEUE_SERVICE = .*|const QUEUE_SERVICE = '$QUEUE_SERVICE_URL';|" "$TESTS_DIR/generate-queue-tokens.js"

    if curl -s -f "$QUEUE_SERVICE_URL/actuator/health" > /dev/null 2>&1; then
        log_success "Queue Service is ready at $QUEUE_SERVICE_URL"
    else
        log_error "Cannot connect to Queue Service"
        log_warning "Tried: http://$QUEUE_SERVICE_IP:8040 and http://localhost:8040"
        log_info "Please check: docker ps | grep queue"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# k6 실행 (타임아웃 추가)
log_info "Running k6 token generation (timeout: 5 minutes)..."
if timeout 300 k6 run "$TESTS_DIR/generate-queue-tokens.js" 2>&1 | tee "$OUTPUTS_DIR/queue-tokens-output.log"; then
    log_success "k6 execution completed"
else
    EXIT_CODE=$?
    if [ $EXIT_CODE -eq 124 ]; then
        log_error "k6 execution timed out after 5 minutes"
    else
        log_error "k6 execution failed with exit code: $EXIT_CODE"
    fi
    exit 1
fi

# 토큰 추출
log_info "Extracting tokens from output..."
grep "✅ User" "$OUTPUTS_DIR/queue-tokens-output.log" | \
  grep -oE '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}' > "$OUTPUTS_DIR/tokens.txt"

TOKEN_COUNT=$(wc -l < "$OUTPUTS_DIR/tokens.txt")
if [ "$TOKEN_COUNT" -ne 1000 ]; then
    log_warning "Expected 1000 tokens, got $TOKEN_COUNT"
    log_info "Check $OUTPUTS_DIR/queue-tokens-output.log for details"
    if [ "$TOKEN_COUNT" -eq 0 ]; then
        log_error "No tokens generated. Aborting."
        exit 1
    fi
fi

log_success "Queue tokens generated: $TOKEN_COUNT"
echo ""

# Step 7: load-test-order.js 업데이트
log_info "Step 7: Updating load-test-order.js..."

# Order Service URL 설정 (감지된 WSL IP 우선)
ORDER_SERVICE_URL="http://$ORDER_SERVICE_IP:8050"

# TEST_DATA 섹션 찾아서 업데이트
sed -i "s/productId: '.*',/productId: '$PRODUCT_ID',/" "$TESTS_DIR/load-test-order.js"
sed -i "s/timeDealId: '.*',/timeDealId: '$TIMEDEAL_ID',/" "$TESTS_DIR/load-test-order.js"

# stockIds 배열 업데이트
STOCK_JS_ARRAY="'${STOCK_ARRAY[0]}',\n        '${STOCK_ARRAY[1]}',\n        '${STOCK_ARRAY[2]}',\n        '${STOCK_ARRAY[3]}'"
sed -i "/stockIds: \[/,/\]/c\    stockIds: [\n        $STOCK_JS_ARRAY\n    ]," "$TESTS_DIR/load-test-order.js"

# ORDER_SERVICE URL 업데이트
log_info "Updating Order Service URL to: $ORDER_SERVICE_URL"
if curl -s -f "$ORDER_SERVICE_URL/actuator/health" > /dev/null 2>&1; then
    log_success "Order Service is ready at $ORDER_SERVICE_URL"
else
    log_warning "Order Service not reachable at $ORDER_SERVICE_URL"

    # localhost로 폴백 시도
    log_info "Trying localhost:8050 as fallback..."
    ORDER_SERVICE_URL="http://localhost:8050"

    if curl -s -f "$ORDER_SERVICE_URL/actuator/health" > /dev/null 2>&1; then
        log_success "Order Service is ready at $ORDER_SERVICE_URL"
    else
        log_warning "Cannot reach Order Service, using $ORDER_SERVICE_URL anyway"
    fi
fi

sed -i "s|const ORDER_SERVICE = .*|const ORDER_SERVICE = '$ORDER_SERVICE_URL';|" "$TESTS_DIR/load-test-order.js"

log_success "load-test-order.js updated"
echo ""

# Step 8: 부하 테스트 실행
log_info "Step 8: Running load test..."
log_warning "This will create 1000 orders (~70% expected to succeed, ~30% to fail due to purchase limit)"
echo ""

read -p "▶️  Press Enter to start load test..."

k6 run "$TESTS_DIR/load-test-order.js" 2>&1 | tee "$OUTPUTS_DIR/load-test-output.log"

log_success "Load test completed"
echo ""

# 바로 검증하면 트랜잭션 커밋이 완료되지 않아 조회 결과가 일치하지 않는 경우가 생겨서 잠시 대기
log_info "Waiting for database transactions to commit..."
sleep 35
echo ""

# ✅ 주문 생성 직후 재고 스냅샷 저장 (Step 8 직후)
log_info "📸 Saving stock snapshot after order creation..."
{
    echo "========================================"
    echo "📦 STOCK COMPARISON REPORT"
    echo "========================================"
    echo ""
    echo "🕐 Part 1: After Order Creation (Step 8)"
    echo "Captured at: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    printf "%-38s | %9s | %9s | %9s | %9s\n" "Stock ID" "Available" "Reserved" "Sold" "Total"
    echo "----------------------------------------|-----------|-----------|-----------|----------"

    docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c "
        SELECT
            id,
            available_stock,
            reserved_stock,
            sold_stock,
            (available_stock + reserved_stock + sold_stock) as total
        FROM time_deal_schema.p_time_deal_stock
        ORDER BY id;
    " | while IFS='|' read -r id avail resv sold total; do
        printf "%-38s | %9s | %9s | %9s | %9s\n" "$id" "$avail" "$resv" "$sold" "$total"
    done

    echo ""
    echo "=========================================="
    echo ""
} > "$OUTPUTS_DIR/stock-comparison.txt"
log_success "Stock snapshot (Part 1) saved"
echo ""


# Step 9: 즉시 검증 (주문 생성 확인)
log_info "Step 9: Initial verification (Order Creation Check)..."
echo ""
log_warning "Checking if orders were actually created..."

# 주문 건수 확인 (전체) - 공백/개행 완전히 제거
ORDER_COUNT=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT COUNT(*) FROM order_schema.p_order;" 2>&1 | tr -d ' \t\n\r')

log_info "Orders created: $ORDER_COUNT"

if [ -z "$ORDER_COUNT" ] || [ "$ORDER_COUNT" = "0" ]; then
    log_error "No orders found in database!"
    log_warning "This indicates order creation failed."

    log_info ""
    log_warning "Possible reasons:"
    log_info "  1. Order Service is not running (check IntelliJ)"
    log_info "  2. API endpoint URL is wrong (check load-test-order.js)"
    log_info "  3. Queue tokens are invalid"
    log_info "  4. Service is rejecting requests"
    log_info "  5. Database connection issue"
    echo ""

    # Order Service가 로컬에서 실행 중인지 확인 (포트 8050)
    log_info "Checking if Order Service is running on port 8050..."
    if netstat -tuln 2>/dev/null | grep -q ":8050 " || ss -tuln 2>/dev/null | grep -q ":8050 "; then
        log_success "✅ Order Service is listening on port 8050"

        # 헬스 체크
        if curl -s -f "http://localhost:8050/actuator/health" > /dev/null 2>&1; then
            log_success "✅ Order Service health check passed"
        else
            log_error "❌ Order Service health check failed"
            log_info "   Try: curl http://localhost:8050/actuator/health"
        fi
    else
        log_error "❌ Order Service is NOT running on port 8050"
        log_info "   Please start Order Service in IntelliJ"
    fi

    echo ""
    log_info "Checking k6 test results..."
    if [ -f "$OUTPUTS_DIR/load-test-output.log" ]; then
        echo ""
        echo "=== k6 Test Summary ==="
        grep -A 20 "checks\|CUSTOM" "$OUTPUTS_DIR/load-test-output.log" | head -30 || echo "No check results found"
        echo ""
        echo "=== Recent Errors ==="
        grep -E "❌|ERROR|error:|failed" "$OUTPUTS_DIR/load-test-output.log" | head -10 || echo "No errors found in k6 log"
        echo ""
        echo "=== Success Count ==="
        grep -E "pending_orders_created|order_success_rate" "$OUTPUTS_DIR/load-test-output.log" || echo "No success metrics found"
        echo ""
    fi

    read -p "Continue verification anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    log_success "✅ Orders successfully created: $ORDER_COUNT"
fi

# 주문 생성 검증 실행
chmod +x "$SCRIPT_DIR/verify-order-creation.sh"
log_info "Running order creation verification..."
echo ""
echo "========================================" | tee "$OUTPUTS_DIR/initial-verification.log"
echo "📊 Initial Verification - Order Creation" | tee -a "$OUTPUTS_DIR/initial-verification.log"
echo "========================================" | tee -a "$OUTPUTS_DIR/initial-verification.log"
echo "" | tee -a "$OUTPUTS_DIR/initial-verification.log"
"$SCRIPT_DIR/verify-order-creation.sh" 2>&1 | tee -a "$OUTPUTS_DIR/initial-verification.log"
echo ""

# Step 10: 자동 취소 대기
log_warning "Step 10: Waiting for auto-cancellation..."
log_info "Waiting 5 minutes for pending order timeout..."
log_info "Then waiting 2 more minutes for scheduler execution..."
echo ""

TOTAL_WAIT=420   # 7분 (5분 타임아웃 + 2분 스케줄러 여유)
INTERVAL=60      # 1분마다 진행 표시

for ((i=0; i<TOTAL_WAIT; i+=INTERVAL)); do
    REMAINING=$((TOTAL_WAIT - i))
    MINUTES=$((REMAINING / 60))
    SECONDS=$((REMAINING % 60))
    printf "\r⏳ Remaining: %02d:%02d" "$MINUTES" "$SECONDS"
    sleep $INTERVAL
done

echo ""
log_success "Waiting completed"
echo ""

# 주문 취소의 경우는 이미 위에서 2분 여유 시간을 줬으므로 5초만 대기
log_info "Waiting for database transactions to commit..."
sleep 5
echo ""

# ✅ 자동 취소 후 재고 스냅샷 저장 (Step 10 직후)
log_info "📸 Saving stock snapshot after auto-cancellation..."
{
    echo "🕐 Part 2: After Auto-Cancellation (Step 10)"
    echo "Captured at: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    printf "%-38s | %9s | %9s | %9s | %9s\n" "Stock ID" "Available" "Reserved" "Sold" "Total"
    echo "----------------------------------------|-----------|-----------|-----------|----------"

    docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c "
        SELECT
            id,
            available_stock,
            reserved_stock,
            sold_stock,
            (available_stock + reserved_stock + sold_stock) as total
        FROM time_deal_schema.p_time_deal_stock
        ORDER BY id;
    " | while IFS='|' read -r id avail resv sold total; do
        printf "%-38s | %9s | %9s | %9s | %9s\n" "$id" "$avail" "$resv" "$sold" "$total"
    done

    echo ""
    echo "=========================================="
    echo "📊 COMPARISON SUMMARY"
    echo "=========================================="
    echo ""
} >> "$OUTPUTS_DIR/stock-comparison.txt"

# 비교 분석 추가
{
    printf "%-38s | %-11s | %-11s | %s\n" "Stock ID" "Before(A/R)" "After(A/R)" "Status"
    echo "----------------------------------------|-------------|-------------|------------"

    # Part 1 데이터 추출
    declare -A stock_before
    while IFS='|' read -r line; do
        if [[ $line =~ ^[a-f0-9]{8}-[a-f0-9]{4} ]]; then
            stock_id=$(echo "$line" | awk '{print $1}')
            avail=$(echo "$line" | awk '{print $3}')
            resv=$(echo "$line" | awk '{print $5}')
            stock_before[$stock_id]="$avail/$resv"
        fi
    done < <(sed -n '/Part 1:/,/Part 2:/p' "$OUTPUTS_DIR/stock-comparison.txt")

    # Part 2 데이터와 비교
    while IFS='|' read -r line; do
        if [[ $line =~ ^[a-f0-9]{8}-[a-f0-9]{4} ]]; then
            stock_id=$(echo "$line" | awk '{print $1}')
            avail_after=$(echo "$line" | awk '{print $3}')
            resv_after=$(echo "$line" | awk '{print $5}')

            before="${stock_before[$stock_id]}"
            IFS='/' read -r avail_before resv_before <<< "$before"

            # 상태 판단
            total_before=$((avail_before + resv_before))
            if [ "$avail_after" = "$total_before" ]; then
                STATUS="✅ RESTORED"
            elif [ "$resv_after" != "0" ]; then
                STATUS="⚠️ RESERVED"
            else
                STATUS="❌ MISMATCH"
            fi

            printf "%-38s | %5s / %-3s  | %5s / %-3s  | %s\n" \
                "$stock_id" \
                "$avail_before" "$resv_before" \
                "$avail_after" "$resv_after" \
                "$STATUS"
        fi
    done < <(sed -n '/Part 2:/,/COMPARISON SUMMARY/p' "$OUTPUTS_DIR/stock-comparison.txt")

    echo ""
    echo "Legend: A=Available, R=Reserved, S=Sold"
    echo "✅ RESTORED: Reserved stock returned to available"
    echo "⚠️ RESERVED: Still has reserved stock"
    echo "❌ MISMATCH: Available stock doesn't match original"
    echo ""
} >> "$OUTPUTS_DIR/stock-comparison.txt"

log_success "Stock comparison report saved: $OUTPUTS_DIR/stock-comparison.txt"
echo ""

# Step 11: 최종 검증 (주문 취소 및 포인트 환불 확인)
log_info "Step 11: Final verification (Order Cancellation & Refund Check)..."
echo ""

# 주문 취소 검증 실행
chmod +x "$SCRIPT_DIR/verify-order-cancellation.sh"
log_info "Running order cancellation verification..."
echo ""
echo "========================================" | tee "$OUTPUTS_DIR/final-verification.log"
echo "📊 Final Verification - Order Cancellation" | tee -a "$OUTPUTS_DIR/final-verification.log"
echo "========================================" | tee -a "$OUTPUTS_DIR/final-verification.log"
echo "" | tee -a "$OUTPUTS_DIR/final-verification.log"
"$SCRIPT_DIR/verify-order-cancellation.sh" 2>&1 | tee -a "$OUTPUTS_DIR/final-verification.log"
echo ""

log_success "===================================="
log_success "🎉 Full test completed successfully!"
log_success "===================================="
echo ""

echo "📊 Test Results:"
echo "   - Initial verification: $OUTPUTS_DIR/initial-verification.log"
echo "   - Final verification: $OUTPUTS_DIR/final-verification.log"
echo "   - Queue tokens: $OUTPUTS_DIR/queue-tokens-output.log"
echo "   - Load test output: $OUTPUTS_DIR/load-test-output.log"
echo "   - Stock comparison: $OUTPUTS_DIR/stock-comparison.txt  ⭐ CHECK THIS!"
echo ""

echo "💡 Quick Summary:"
echo ""
echo "📊 1. ORDER STATUS"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH recent_orders AS (
    SELECT order_id, status, user_id
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT 1000
)
SELECT
    status,
    COUNT(*) as count,
    ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER () * 100, 1) as percentage
FROM recent_orders
GROUP BY status
ORDER BY count DESC;
"

echo ""
echo "💰 2. POINT TRANSACTION"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
WITH recent_orders AS (
    SELECT user_id
    FROM order_schema.p_order
    ORDER BY created_at DESC
    LIMIT 1000
)
SELECT
    type,
    COUNT(*) as count,
    SUM(amount) as total_amount
FROM user_schema.p_point_history ph
WHERE ph.user_id IN (SELECT DISTINCT user_id FROM recent_orders)
  AND type IN ('USE_PENDING', 'USE_CANCEL', 'EARN_PENDING')
GROUP BY type
ORDER BY
    CASE type
        WHEN 'USE_PENDING' THEN 1
        WHEN 'USE_CANCEL' THEN 2
        WHEN 'EARN_PENDING' THEN 3
    END;
"

echo ""
echo "📦 3. STOCK COMPARISON"
echo ""
if [ -f "$OUTPUTS_DIR/stock-comparison.txt" ]; then
    cat "$OUTPUTS_DIR/stock-comparison.txt"
else
    log_error "Stock comparison file not found"
fi

echo ""
echo "🔍 For detailed analysis:"
echo "   - Order Creation:    cat $OUTPUTS_DIR/initial-verification.log  ⭐"
echo "   - Order Cancellation: cat $OUTPUTS_DIR/final-verification.log  ⭐"
echo "   - Stock Comparison:  cat $OUTPUTS_DIR/stock-comparison.txt  ⭐"
echo "   - Point Refund:      $SCRIPT_DIR/check-point-refund.sh"
