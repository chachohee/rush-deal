// load-test-order.js
// RushDeal 주문 시스템 부하테스트
// 게이트웨이 없음, 사전 발급된 큐 토큰 사용

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ===== 큐 토큰 로드 =====
const queueTokens = new SharedArray('tokens', function() {
    // tokens.txt 파일에서 토큰 로드
    const data = open('./tokens.txt').split('\n').filter(t => t.trim());
    console.log(`✅ Loaded ${data.length} queue tokens`);
    return data;
});

// ===== 커스텀 메트릭 =====
const orderSuccessRate = new Rate('order_success_rate');
const orderFailureRate = new Rate('order_failure_rate');
const orderDuration = new Trend('order_duration_ms');

// 에러 분류
const stockErrors = new Counter('stock_errors');
const pointErrors = new Counter('point_errors');
const queueErrors = new Counter('queue_errors');
const purchaseLimitErrors = new Counter('purchase_limit_errors');
const sagaFailures = new Counter('saga_failures');

// 실시간 추적
const pendingOrders = new Counter('pending_orders_created');
const totalOrderQuantity = new Counter('total_order_quantity');

// ===== 테스트 설정 =====
export const options = {
    scenarios: {
        order_rush: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },   // 10초 동안 50명까지
                { duration: '20s', target: 100 },  // 20초 동안 100명까지
                { duration: '30s', target: 100 },  // 30초 유지
            ],
            gracefulRampDown: '10s',
        },
    },

    thresholds: {
        'http_req_duration': ['p(95)<5000', 'p(99)<10000'],
        'http_req_failed': ['rate<0.5'],
        'order_success_rate': ['rate>0.5'],
        'order_duration_ms': ['p(95)<8000'],
    },
};

// ===== 테스트 데이터 =====
const TEST_USERS = 100;

// ⚠️ ./get-test-ids.sh 실행 결과로 교체
const TEST_DATA = {
    productId: '932a6cdb-478f-422a-8f8f-3da1243c07d6',
    timeDealId: '9fcce579-4f53-40cf-afc9-d016a5db6251',

    // 4개 옵션의 재고 ID (get-test-ids.sh 결과)
    stockIds: [
        '6774176a-5069-4275-ad19-ebb09bc1e445',  // 옵션 1
        '5be12941-254c-44de-846d-d2bdc05d0d79',  // 옵션 2
        '636c97bf-aaa3-4f25-bd49-51e6f91cc830',  // 옵션 3
        '1e533629-835c-47fc-9aac-ab8347ea4af4',  // 옵션 4
    ],

    productPrice: 95200,
    pointToUse: 1000,
};

// 서비스 URL (WSL2에서 Windows로 접근)
const ORDER_SERVICE = 'http://172.30.1.72:8050';

// ===== Setup =====
export function setup() {
    console.log('🚀 RushDeal Order Load Test');
    console.log('============================');
    console.log(`Target Users: ${TEST_USERS}`);
    console.log(`Loaded Tokens: ${queueTokens.length}`);
    console.log(`Product: ${TEST_DATA.productId}`);
    console.log(`TimeDeal: ${TEST_DATA.timeDealId}`);
    console.log(`Total Stock: 400 units (4 options × 100)`);
    console.log(`Price: ${TEST_DATA.productPrice}원`);
    console.log(`Point Discount: ${TEST_DATA.pointToUse}원`);
    console.log('');

    // Order Service 헬스체크
    const orderHealth = http.get(`${ORDER_SERVICE}/actuator/health`, { timeout: '10s' });
    if (orderHealth.status !== 200) {
        throw new Error(`Order Service is not healthy: ${orderHealth.status}`);
    }
    console.log('✅ Order Service is healthy');
    console.log('');

    if (queueTokens.length < TEST_USERS) {
        throw new Error(`Not enough tokens: ${queueTokens.length} < ${TEST_USERS}`);
    }

    return { testData: TEST_DATA };
}

// ===== Main Test =====
export default function(data) {
    const userId = ((__VU - 1) % TEST_USERS) + 1;
    const testData = data.testData;

    // 각 VU에 해당하는 큐 토큰 사용
    const queueToken = queueTokens[userId - 1];

    if (!queueToken) {
        console.error(`❌ [User ${userId}] No queue token available`);
        return;
    }

    // 랜덤으로 재고 옵션 선택
    const randomStockId = testData.stockIds[
        Math.floor(Math.random() * testData.stockIds.length)
        ];

    // 랜덤으로 주문 수량 결정 (1~3개)
    const quantity = Math.floor(Math.random() * 3) + 1;

    // ========================================
    // 주문 생성
    // ========================================
    const orderPayload = JSON.stringify({
        timeDealId: testData.timeDealId,
        productId: testData.productId,
        orderItems: [{
            timeDealStockId: randomStockId,
            quantity: quantity
        }],
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

    const orderStartTime = Date.now();
    const orderRes = http.post(
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
            tags: { name: 'CreateOrder' },
            timeout: '15s',
        }
    );

    const orderDurationMs = Date.now() - orderStartTime;
    orderDuration.add(orderDurationMs);

    // ========================================
    // 결과 검증
    // ========================================
    const orderSuccess = check(orderRes, {
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

    orderSuccessRate.add(orderSuccess ? 1 : 0);
    orderFailureRate.add(orderSuccess ? 0 : 1);

    if (orderSuccess) {
        const body = JSON.parse(orderRes.body);
        const sagaId = body.data.sagaId;
        pendingOrders.add(1);
        totalOrderQuantity.add(quantity);

        // 10번에 1번만 로그 출력
        if (__ITER % 10 === 0) {
            console.log(`✅ [User ${userId}] Order created - Saga: ${sagaId.substring(0, 8)}..., Qty: ${quantity}, Duration: ${orderDurationMs}ms`);
        }
    } else {
        // 에러 분류
        const body = orderRes.body || '';
        const status = orderRes.status;

        if (body.includes('재고') || body.includes('stock') || body.includes('OUT_OF_STOCK')) {
            stockErrors.add(1);
            console.error(`❌ [User ${userId}] Stock error - Qty: ${quantity}`);
        } else if (body.includes('포인트') || body.includes('point') || body.includes('POINT')) {
            pointErrors.add(1);
            console.error(`❌ [User ${userId}] Point error`);
        } else if (body.includes('제한') || body.includes('limit') || body.includes('LIMIT')) {
            purchaseLimitErrors.add(1);
            console.error(`❌ [User ${userId}] Purchase limit exceeded - Qty: ${quantity}`);
        } else if (body.includes('토큰') || body.includes('token') || body.includes('TOKEN') || status === 403) {
            queueErrors.add(1);
            console.error(`❌ [User ${userId}] Queue token error`);
        } else {
            sagaFailures.add(1);
            console.error(`❌ [User ${userId}] Order failed - Status: ${status}, Body: ${body.substring(0, 100)}`);
        }
    }

    // 다음 요청까지 랜덤 대기 (1~3초)
    sleep(Math.random() * 2 + 1);
}

// ===== Teardown =====
export function teardown(data) {
    console.log('');
    console.log('🏁 Load Test Completed!');
    console.log('========================');
    console.log('');
    console.log('📊 Summary:');
    console.log(`  - Total Orders Created: ${pendingOrders.value || 0}`);
    console.log(`  - Total Quantity: ${totalOrderQuantity.value || 0}`);
    console.log(`  - Stock Errors: ${stockErrors.value || 0}`);
    console.log(`  - Queue Errors: ${queueErrors.value || 0}`);
    console.log(`  - Point Errors: ${pointErrors.value || 0}`);
    console.log(`  - Purchase Limit Errors: ${purchaseLimitErrors.value || 0}`);
    console.log(`  - Saga Failures: ${sagaFailures.value || 0}`);
    console.log('');
    console.log('📈 Check detailed metrics in k6 output above');
    console.log('');
}
