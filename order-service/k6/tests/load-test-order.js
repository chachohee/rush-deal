// load-test-order.js
// RushDeal 주문 시스템 부하테스트
// 게이트웨이 없음, 사전 발급된 큐 토큰 사용

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// ===== 큐 토큰 로드 =====
// SharedArray: 모든 VU가 공유하는 읽기 전용 배열 (메모리 효율적)
const queueTokens = new SharedArray('tokens', function() {
    // tokens.txt 파일에서 토큰 로드 (각 줄이 하나의 토큰)
    const data = open('../outputs/tokens.txt').split('\n').filter(t => t.trim());
    console.log(`✅ Loaded ${data.length} queue tokens`);
    return data;
});

// ===== 커스텀 메트릭 정의 =====
// Rate: 성공/실패 비율을 추적 (0~1 사이 값)
const orderSuccessRate = new Rate('order_success_rate');
const orderFailureRate = new Rate('order_failure_rate');

// Trend: 응답 시간 같은 연속된 값의 통계 (평균, p95, p99 등)
const orderDuration = new Trend('order_duration_ms');

// Counter: 누적 카운트를 추적 (증가만 가능)
// 에러 타입별 카운터
const stockErrors = new Counter('stock_errors');              // 재고 부족 에러
const pointErrors = new Counter('point_errors');              // 포인트 부족 에러
const queueErrors = new Counter('queue_errors');              // 큐 토큰 에러
const purchaseLimitErrors = new Counter('purchase_limit_errors'); // 구매 제한 초과 (의도된 테스트)
const timeDealErrors = new Counter('timedeal_errors');        // 타임딜 에러
const sagaFailures = new Counter('saga_failures');            // 기타 예상치 못한 에러

// 주문 통계 카운터
const pendingOrders = new Counter('pending_orders_created');  // 생성된 주문 수
const totalOrderQuantity = new Counter('total_order_quantity'); // 총 주문 수량

// ===== 테스트 시나리오 설정 =====
export const options = {
    scenarios: {
        order_rush: {
            // per-vu-iterations: 각 VU가 지정된 횟수만큼 반복 실행
            executor: 'per-vu-iterations',
            vus: 100,              // Virtual Users: 동시 접속 유저 수
            iterations: 1,         // 각 VU가 1번만 주문 (총 100개 주문)
            maxDuration: '2m',     // 최대 실행 시간 (2분)
        },
    },

    // 성능 임계값 정의 (실패 시 테스트 전체가 실패로 처리)
    thresholds: {
        'http_req_duration': ['p(95)<5000', 'p(99)<10000'], // 95%는 5초 이내, 99%는 10초 이내
        'http_req_failed': ['rate<0.5'],                     // HTTP 실패율 50% 미만
        'order_success_rate': ['rate>0.5'],                  // 주문 성공률 50% 이상
        'order_duration_ms': ['p(95)<8000'],                 // 주문 처리 시간 95%는 8초 이내
    },
};

// ===== 테스트 데이터 상수 =====
const TEST_USERS = 100;

// ⚠️ 주의: ./get-test-ids.sh 스크립트 실행 결과로 교체 필요
const TEST_DATA = {
    productId: '857daa45-d8a7-4e13-938e-5ea80dab2238',    // 테스트 상품 ID
    timeDealId: 'a82bc130-66d3-4306-9c92-b6864b7436a5',   // 타임딜 ID

    // 4개 옵션의 재고 ID (각 옵션당 100개씩, 총 400개 재고)
    stockIds: [
        '42d44908-6b83-4ff1-8068-e9b4efe47629',
        'ae7993be-386f-4d66-90f3-61866b4fdb30',
        '57eff965-669e-4aea-86fb-d3eb92467d0e',
        '46e7e4db-2ee1-48da-96cb-c672f986f230'
    ],

    productPrice: 95200,   // 상품 가격
    pointToUse: 1000,      // 사용할 포인트
    purchaseLimit: 5,      // 1인당 최대 구매 제한 수량
};

// 서비스 URL (WSL2에서 Windows로 접근)
// cmd에서 "ipconfig | findstr IPv4" 실행하여 IP 확인
const ORDER_SERVICE = 'http://172.30.1.100:8050';

// ===== Setup 단계 =====
// 테스트 시작 전 1회만 실행되는 초기화 함수
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
    console.log(`Purchase Limit: ${TEST_DATA.purchaseLimit}개/인`);
    console.log('');

    // Order Service 헬스체크: 서비스가 정상 동작하는지 확인
    const orderHealth = http.get(`${ORDER_SERVICE}/actuator/health`, { timeout: '10s' });
    if (orderHealth.status !== 200) {
        throw new Error(`Order Service is not healthy: ${orderHealth.status}`);
    }
    console.log('✅ Order Service is healthy');
    console.log('');

    // 토큰 수 검증: 100명의 유저를 위한 100개 토큰 필요
    if (queueTokens.length < 100) {
        throw new Error(`Not enough tokens: ${queueTokens.length} < 100`);
    }

    console.log(`✅ Ready: 100 users will place orders simultaneously`);
    console.log(`   - 약 70%는 정상 주문 (1~5개)`);
    console.log(`   - 약 30%는 제한 초과 테스트 (6~9개)`);
    console.log('');

    // setup()의 반환값은 default 함수의 data 파라미터로 전달됨
    return { testData: TEST_DATA };
}

// ===== Main Test 함수 =====
// 각 VU가 반복 실행하는 메인 테스트 로직
export default function(data) {
    const testData = data.testData;

    // __VU: k6 내장 변수, 현재 VU의 ID (1부터 시작)
    const userId = __VU;

    // 각 VU는 자신의 고유 토큰 사용 (배열 인덱스는 0부터 시작하므로 -1)
    const queueToken = queueTokens[userId - 1];

    if (!queueToken) {
        console.error(`❌ [User ${userId}] No queue token available`);
        return;
    }

    // ===== 주문 아이템 생성 로직 =====
    // 30% 확률로 구매 제한 초과 테스트, 70%는 정상 주문
    const shouldTestLimit = Math.random() < 0.3;
    let orderItems = [];

    if (shouldTestLimit) {
        // 구매 제한 초과 테스트: 6~9개 주문 (제한: 5개)
        const numItems = Math.floor(Math.random() * 3) + 2; // 2~4개 옵션 선택
        const availableStocks = [...testData.stockIds];     // 스프레드 연산자로 배열 복사

        for (let i = 0; i < numItems && availableStocks.length > 0; i++) {
            // 랜덤하게 옵션 선택 및 제거 (중복 방지)
            const randomIndex = Math.floor(Math.random() * availableStocks.length);
            const stockId = availableStocks.splice(randomIndex, 1)[0];
            const quantity = Math.floor(Math.random() * 3) + 2; // 2~4개씩

            orderItems.push({
                timeDealStockId: stockId,
                quantity: quantity
            });
        }
    } else {
        // 정상 주문: 1~5개
        const numItems = Math.floor(Math.random() * 3) + 1; // 1~3개 옵션 선택
        const availableStocks = [...testData.stockIds];

        for (let i = 0; i < numItems && availableStocks.length > 0; i++) {
            const randomIndex = Math.floor(Math.random() * availableStocks.length);
            const stockId = availableStocks.splice(randomIndex, 1)[0];
            const quantity = Math.floor(Math.random() * 2) + 1; // 1~2개씩

            orderItems.push({
                timeDealStockId: stockId,
                quantity: quantity
            });
        }

        // 정상 범위 내로 조정: 총 수량이 제한을 초과하면 줄이기
        let totalQty = orderItems.reduce((sum, item) => sum + item.quantity, 0);
        while (totalQty > testData.purchaseLimit && orderItems.length > 0) {
            // 마지막 아이템의 수량을 1개씩 줄임
            orderItems[orderItems.length - 1].quantity--;
            // 수량이 0이 되면 해당 아이템 제거
            if (orderItems[orderItems.length - 1].quantity === 0) {
                orderItems.pop();
            }
            totalQty = orderItems.reduce((sum, item) => sum + item.quantity, 0);
        }
    }

    // 총 주문 수량 계산
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

    // ===== 주문 생성 API 호출 =====
    const orderStartTime = Date.now(); // 응답 시간 측정 시작
    const orderRes = http.post(
        `${ORDER_SERVICE}/api/v1/orders`,
        orderPayload,
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId.toString(),              // 사용자 ID 헤더
                'X-User-Email': `testuser${userId}@test.com`,
                'X-User-Role': 'USER',
                'X-Queue-Token': queueToken                  // 큐 토큰 (필수)
            },
            tags: { name: 'CreateOrder' },  // 메트릭 그룹화용 태그
            timeout: '15s',                 // 15초 타임아웃
        }
    );

    // 응답 시간 계산 및 메트릭 기록
    const orderDurationMs = Date.now() - orderStartTime;
    orderDuration.add(orderDurationMs);

    // ===== 응답 검증 =====
    const orderSuccess = check(orderRes, {
        // 체크 1: HTTP 상태 코드가 200 또는 201인지
        'order: status 200 or 201': (r) => r.status === 200 || r.status === 201,
        // 체크 2: 응답 바디에 sagaId가 포함되어 있는지
        'order: has sagaId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.sagaId;
            } catch (e) {
                return false;
            }
        },
    });

    // 성공/실패 메트릭 기록
    orderSuccessRate.add(orderSuccess ? 1 : 0);
    orderFailureRate.add(orderSuccess ? 0 : 1);

    // ===== 결과 처리 =====
    if (orderSuccess) {
        // 성공한 경우
        const body = JSON.parse(orderRes.body);
        const sagaId = body.data.sagaId;

        // 성공 메트릭 업데이트
        pendingOrders.add(1);
        totalOrderQuantity.add(totalQuantity);

        // 주문 내역을 사람이 읽기 쉬운 형태로 변환
        // 예: [Stock1×2, Stock3×1]
        const itemsDesc = orderItems.map(item =>
            `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
        ).join(', ');

        // 제한 초과 테스트인 경우 🎯, 정상 주문은 ✅
        const badge = totalQuantity > testData.purchaseLimit ? '🎯' : '✅';
        console.log(`${badge} [User ${userId}] Order created - Saga: ${sagaId.substring(0, 8)}..., Items: [${itemsDesc}], Total: ${totalQuantity}, Duration: ${orderDurationMs}ms`);
    } else {
        // 실패한 경우: 에러 분류 및 로깅
        classifyAndLogError(userId, orderRes, orderItems, totalQuantity, testData);
    }
}

// ===== 에러 분류 및 로깅 함수 =====
/**
 * 응답 에러를 ErrorCode 기반으로 정확하게 분류하고 로깅
 *
 * @param {number} userId - 사용자 ID
 * @param {object} orderRes - HTTP 응답 객체
 * @param {array} orderItems - 주문 아이템 배열
 * @param {number} totalQuantity - 총 주문 수량
 * @param {object} testData - 테스트 데이터
 */
function classifyAndLogError(userId, orderRes, orderItems, totalQuantity, testData) {
    const status = orderRes.status;
    const body = orderRes.body || '';

    let errorCode = '';
    let errorMessage = '';

    // 응답 바디에서 에러 정보 추출
    try {
        const errorBody = JSON.parse(body);

        // ✅ ApiResponse<ErrorResponse> 구조
        // { success: false, data: { code: "ERROR_CODE", message: "..." } }
        errorCode = errorBody.data?.code || '';
        errorMessage = errorBody.data?.message || '';

    } catch (e) {
        // JSON 파싱 실패 시 원본 텍스트 사용 (최대 200자)
        errorMessage = body.substring(0, 200);
    }

    // 주문 아이템을 사람이 읽기 쉬운 형태로 변환
    const itemsDesc = orderItems.map(item =>
        `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
    ).join(', ');

    // ===== ErrorCode 기반 에러 분류 =====
    // 각 에러 타입에 맞는 Counter 증가 및 상세 로그 출력

    if (errorCode === 'PURCHASE_LIMIT_EXCEEDED') {
        // 구매 제한 초과 (의도된 테스트 케이스)
        purchaseLimitErrors.add(1);
        console.error(`🎯 [User ${userId}] Purchase limit exceeded (Expected)`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity} > Limit: ${testData.purchaseLimit}`);

    } else if (errorCode === 'STOCK_DEPLETED' || errorCode === 'SOLD_OUT_PRODUCT') {
        // 재고 부족 에러
        stockErrors.add(1);
        console.error(`❌ [User ${userId}] Stock error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'NOT_ENOUGH_POINTS') {
        // 포인트 부족 에러
        pointErrors.add(1);
        console.error(`❌ [User ${userId}] Point error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'INVALID_QUEUE_TOKEN' || status === 403) {
        // 큐 토큰 에러 (ErrorCode 또는 403 Forbidden 상태)
        queueErrors.add(1);
        console.error(`❌ [User ${userId}] Queue token error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else if (errorCode === 'INVALID_TIME_DEAL') {
        // 타임딜 에러 (종료되었거나 유효하지 않음)
        timeDealErrors.add(1);
        console.error(`❌ [User ${userId}] TimeDeal error`);
        console.error(`    ErrorCode: ${errorCode}`);
        console.error(`    Message: ${errorMessage}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);

    } else {
        // 기타 예상치 못한 에러 (500 Internal Server Error 등)
        sagaFailures.add(1);
        console.error(`❌ [User ${userId}] Order failed - Status: ${status}`);
        console.error(`    ErrorCode: ${errorCode || 'UNKNOWN'}`);
        console.error(`    Message: ${errorMessage || 'No error message'}`);
        console.error(`    Attempted: [${itemsDesc}], Total: ${totalQuantity}`);
    }
}

// ===== Teardown 단계 =====
// 테스트 종료 후 1회만 실행되는 정리 함수
export function teardown(data) {
    console.log('');
    console.log('🏁 Load Test Completed!');
    console.log('========================');
    console.log('');
    console.log('📊 Summary (check CUSTOM metrics above for accurate counts):');
    console.log('  - pending_orders_created: Successful orders');
    console.log('  - total_order_quantity: Total items ordered');
    console.log('  - purchase_limit_errors: Purchase limit exceeded (expected ~30%)');
    console.log('  - stock_errors: Stock depletion errors');
    console.log('  - point_errors: Insufficient points errors');
    console.log('  - queue_errors: Invalid queue token errors');
    console.log('  - saga_failures: Other unexpected failures');
    console.log('');
    console.log('💡 Tip: All error counts are in CUSTOM RESULTS section');
    console.log('');
}
