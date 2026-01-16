// load-test-order.js
// RushDeal 주문 시스템 부하테스트
// 게이트웨이 없음, 사전 발급된 큐 토큰 사용

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ===== 큐 토큰 로드 =====
const queueTokens = new SharedArray('tokens', function() {
    const data = open('../outputs/tokens.txt').split('\n').filter(t => t.trim());
    console.log(`✅ Loaded ${data.length} queue tokens`);
    return data;
});

// ===== 커스텀 메트릭 정의 =====
const orderSuccessRate = new Rate('order_success_rate');
const orderFailureRate = new Rate('order_failure_rate');
const orderDuration = new Trend('order_duration_ms');

// 에러 타입별 카운터
const stockErrors = new Counter('stock_errors');
const pointErrors = new Counter('point_errors');
const queueErrors = new Counter('queue_errors');
const purchaseLimitErrors = new Counter('purchase_limit_errors');
const timeDealErrors = new Counter('timedeal_errors');
const sagaFailures = new Counter('saga_failures');

// 주문 통계 카운터
const pendingOrders = new Counter('pending_orders_created');
const totalOrderQuantity = new Counter('total_order_quantity');

// ===== 재시도 설정 =====
const MAX_RETRIES = 3;      // 최대 3회 재시도
const RETRY_DELAY = 0.5;    // 0.5초 대기

// ===== 테스트 시나리오 설정 =====
export const options = {
    scenarios: {
        order_rush: {
            executor: 'per-vu-iterations',
            vus: 1000,
            iterations: 1,
            maxDuration: '5m',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<5000', 'p(99)<10000'],
        http_req_failed: ['rate<0.1'],           // 재시도 로직으로 10% 미만 목표
        order_success_rate: ['rate>0.9'],        // 90% 이상 성공 목표
        order_duration_ms: ['p(95)<8000'],
    },
};

// ===== 테스트 데이터 상수 =====
const TEST_USERS = 1000;

const TEST_DATA = {
    productId: '017b6d7f-9f46-4f75-a039-f0e6c7cf9826',
    timeDealId: '99112350-0122-4966-8bfc-01c57b091880',
    stockIds: [
        '34aa164d-8bbb-4322-8a85-fc33c00bbaea',
        '91ca85d1-b750-46da-b36a-b5b8de6aea5b',
        '7e0d0623-e03f-4335-85c9-3072a20572e6',
        '3b80ff15-c8fe-46ce-8777-2d475ac06ee0'
    ],
    productPrice: 95200,
    pointToUse: 1000,
    purchaseLimit: 5,
};

const ORDER_SERVICE = 'http://172.30.1.72:8050';

// ===== Setup 단계 =====
export function setup() {
    console.log('🚀 RushDeal Order Load Test (with Retry Logic)');
    console.log('================================================');
    console.log(`Target Users: ${TEST_USERS}`);
    console.log(`Loaded Tokens: ${queueTokens.length}`);
    console.log(`Max Retries: ${MAX_RETRIES}`);
    console.log(`Product: ${TEST_DATA.productId}`);
    console.log(`TimeDeal: ${TEST_DATA.timeDealId}`);
    console.log(`Total Stock: 4000 units (4 options × 1000)`);
    console.log('');

    const orderHealth = http.get(`${ORDER_SERVICE}/actuator/health`, { timeout: '10s' });
    if (orderHealth.status !== 200) {
        throw new Error(`Order Service is not healthy: ${orderHealth.status}`);
    }
    console.log('✅ Order Service is healthy');
    console.log('');

    if (queueTokens.length < 1000) {
        throw new Error(`Not enough tokens: ${queueTokens.length} < 1000`);
    }

    console.log(`✅ Ready: 1000 users will place orders simultaneously`);
    console.log(`   - 약 70%는 정상 주문 (1~5개)`);
    console.log(`   - 약 30%는 제한 초과 테스트 (6~9개)`);
    console.log(`   - Connection Refused 시 최대 ${MAX_RETRIES}회 재시도`);
    console.log('');

    return { testData: TEST_DATA };
}

// ===== Main Test 함수 (재시도 로직 추가) =====
export default function(data) {
    const testData = data.testData;
    const userId = __VU;
    const queueToken = queueTokens[userId - 1];

    if (!queueToken) {
        console.error(`❌ [User ${userId}] No queue token available`);
        return;
    }

    // ===== 주문 아이템 생성 로직 =====
    const shouldTestLimit = Math.random() < 0.3;
    let orderItems = [];

    if (shouldTestLimit) {
        const numItems = Math.floor(Math.random() * 3) + 2;
        const availableStocks = [...testData.stockIds];

        for (let i = 0; i < numItems && availableStocks.length > 0; i++) {
            const randomIndex = Math.floor(Math.random() * availableStocks.length);
            const stockId = availableStocks.splice(randomIndex, 1)[0];
            const quantity = Math.floor(Math.random() * 3) + 2;

            orderItems.push({
                timeDealStockId: stockId,
                quantity: quantity
            });
        }
    } else {
        const numItems = Math.floor(Math.random() * 3) + 1;
        const availableStocks = [...testData.stockIds];

        for (let i = 0; i < numItems && availableStocks.length > 0; i++) {
            const randomIndex = Math.floor(Math.random() * availableStocks.length);
            const stockId = availableStocks.splice(randomIndex, 1)[0];
            const quantity = Math.floor(Math.random() * 2) + 1;

            orderItems.push({
                timeDealStockId: stockId,
                quantity: quantity
            });
        }

        let totalQty = orderItems.reduce((sum, item) => sum + item.quantity, 0);
        while (totalQty > testData.purchaseLimit && orderItems.length > 0) {
            orderItems[orderItems.length - 1].quantity--;
            if (orderItems[orderItems.length - 1].quantity === 0) {
                orderItems.pop();
            }
            totalQty = orderItems.reduce((sum, item) => sum + item.quantity, 0);
        }
    }

    const totalQuantity = orderItems.reduce((sum, item) => sum + item.quantity, 0);

    // ===== HTTP 요청 페이로드 구성 =====
    const orderPayload = JSON.stringify({
        timeDealId: testData.timeDealId,
        productId: testData.productId,
        orderItems: orderItems,
        pointUsed: testData.pointToUse,
        shippingInfo: {
            recipientName: `테스터${userId}`,
            recipientPhone: '01012345678',
            zipCode: '12345',
            addressBase: '서울시 강남구 테스트로 123',
            addressDetail: `${userId}동 ${userId}호`,
            deliveryMessage: '문앞에 놓아주세요'
        }
    });

    // ===== 재시도 로직 =====
    let retries = MAX_RETRIES;
    let orderSuccess = false;
    let orderRes = null;
    let finalDurationMs = 0;

    while (retries > 0 && !orderSuccess) {
        const orderStartTime = Date.now();

        orderRes = http.post(
            `${ORDER_SERVICE}/api/v1/orders`,
            orderPayload,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': userId.toString(),
                    'X-User-Email': `testuser${userId}@test.com`,
                    'X-User-Role': 'USER',
                    'X-Queue-Token': queueToken
                },
                tags: {
                    name: 'CreateOrder',
                    retry: (MAX_RETRIES - retries + 1).toString()
                },
                timeout: '15s',
            }
        );

        const orderDurationMs = Date.now() - orderStartTime;
        finalDurationMs = orderDurationMs;

        // ===== 응답 검증 =====
        orderSuccess = check(orderRes, {
            'order: status 200 or 201': (r) => r.status === 200 || r.status === 201,
            'order: has sagaId': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.data && body.data.sagaId;
                } catch (e) {
                    return false;
                }
            },
        });

        if (orderSuccess) {
            // 성공 시 메트릭 기록 및 루프 종료
            orderDuration.add(orderDurationMs);

            const body = JSON.parse(orderRes.body);
            const sagaId = body.data.sagaId;
            pendingOrders.add(1);
            totalOrderQuantity.add(totalQuantity);

            const itemsDesc = orderItems.map(item =>
                `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
            ).join(', ');

            const badge = totalQuantity > testData.purchaseLimit ? '🎯' : '✅';
            const attemptInfo = MAX_RETRIES - retries + 1 > 1
                ? ` (attempt ${MAX_RETRIES - retries + 1})`
                : '';

            console.log(`${badge} [User ${userId}]${attemptInfo} Order created - Saga: ${sagaId.substring(0, 8)}..., Items: [${itemsDesc}], Total: ${totalQuantity}, Duration: ${orderDurationMs}ms`);
            break;
        }

        // 실패한 경우
        retries--;

        // Connection Refused 에러인 경우에만 재시도
        const isConnectionRefused = orderRes.status === 0;

        if (retries > 0 && isConnectionRefused) {
            const itemsDesc = orderItems.map(item =>
                `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
            ).join(', ');

            console.log(`⚠️ [User ${userId}] Connection refused - Retry ${MAX_RETRIES - retries}/${MAX_RETRIES} after ${RETRY_DELAY}s (Items: [${itemsDesc}])`);
            sleep(RETRY_DELAY);
        } else if (!isConnectionRefused) {
            // Connection Refused가 아닌 다른 에러는 재시도하지 않고 바로 분류
            break;
        }
    }

    // ===== 최종 결과 처리 =====
    if (orderSuccess) {
        orderSuccessRate.add(1);
        orderFailureRate.add(0);
    } else {
        orderSuccessRate.add(0);
        orderFailureRate.add(1);
        orderDuration.add(finalDurationMs);

        // 모든 재시도 실패 시 에러 분류
        if (retries === 0) {
            console.error(`❌ [User ${userId}] All retries failed`);
        }
        classifyAndLogError(userId, orderRes, orderItems, totalQuantity, testData);
    }
}

// ===== 에러 분류 및 로깅 함수 =====
function classifyAndLogError(userId, orderRes, orderItems, totalQuantity, testData) {
    const status = orderRes.status;
    const body = orderRes.body || '';

    let errorCode = '';
    let errorMessage = '';

    try {
        const errorBody = JSON.parse(body);
        errorCode = errorBody.data?.code || '';
        errorMessage = errorBody.data?.message || '';
    } catch (e) {
        errorMessage = body.substring(0, 200);
    }

    const itemsDesc = orderItems.map(item =>
        `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
    ).join(', ');

    if (errorCode === 'PURCHASE_LIMIT_EXCEEDED') {
        purchaseLimitErrors.add(1);
        console.error(`🎯 [User ${userId}] Purchase limit exceeded (Expected)`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity} > Limit: ${testData.purchaseLimit}`);

    } else if (errorCode === 'STOCK_DEPLETED' || errorCode === 'SOLD_OUT_PRODUCT') {
        stockErrors.add(1);
        console.error(`❌ [User ${userId}] Stock error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'NOT_ENOUGH_POINTS') {
        pointErrors.add(1);
        console.error(`❌ [User ${userId}] Point error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'INVALID_QUEUE_TOKEN' || status === 403) {
        queueErrors.add(1);
        console.error(`❌ [User ${userId}] Queue token error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'INVALID_TIME_DEAL') {
        timeDealErrors.add(1);
        console.error(`❌ [User ${userId}] TimeDeal error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else {
        sagaFailures.add(1);
        console.error(`❌ [User ${userId}] Order failed - Status: ${status}`);
        console.error(`    ErrorCode: ${errorCode || 'UNKNOWN'}`);
        console.error(`    Message: ${errorMessage || 'No error message'}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);
    }
}

// ===== Teardown 단계 =====
export function teardown() {
    console.log('');
    console.log('🏁 Load Test Completed!');
    console.log('========================');
    console.log('');
    console.log('📊 Summary (1000 Users Test with Retry)');
    console.log('  - pending_orders_created: Successful orders');
    console.log('  - total_order_quantity: Total items ordered');
    console.log('  - purchase_limit_errors: Purchase limit exceeded (~30%)');
    console.log('  - stock_errors: Stock depletion errors');
    console.log('  - point_errors: Insufficient points errors');
    console.log('  - queue_errors: Invalid queue token errors');
    console.log('  - saga_failures: Other unexpected failures');
    console.log('');
}
