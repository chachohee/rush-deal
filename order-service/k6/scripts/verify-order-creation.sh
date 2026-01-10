#!/bin/bash
# run-full-test.sh - Step 9만 수정한 버전
# 파일 전체를 교체하지 말고, Step 9 부분만 아래 내용으로 교체하세요

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
    if [ -f "load-test-output.log" ]; then
        echo ""
        echo "=== k6 Test Summary ==="
        grep -A 20 "checks\|CUSTOM" load-test-output.log | head -30 || echo "No check results found"
        echo ""
        echo "=== Recent Errors ==="
        grep -E "❌|ERROR|error:|failed" load-test-output.log | head -10 || echo "No errors found in k6 log"
        echo ""
        echo "=== Success Count ==="
        grep -E "pending_orders_created|order_success_rate" load-test-output.log || echo "No success metrics found"
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
chmod +x verify-order-creation.sh
log_info "Running order creation verification..."
echo ""
echo "========================================" | tee initial-verification.log
echo "📊 Initial Verification - Order Creation" | tee -a initial-verification.log
echo "========================================" | tee -a initial-verification.log
echo "" | tee -a initial-verification.log
./verify-order-creation.sh 100 2>&1 | tee -a initial-verification.log
echo ""
