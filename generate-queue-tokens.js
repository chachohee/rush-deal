// generate-queue-tokens.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        generate_tokens: {
            executor: 'per-vu-iterations',  // ✅ 각 VU가 정확히 1번씩 실행
            vus: 100,
            iterations: 1,  // 각 VU당 1번
            maxDuration: '2m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<5000'],
        'checks': ['rate>0.90'],
    },
};

const QUEUE_SERVICE = 'http://172.30.1.72:8040';
const TEST_USERS = 100;
const PRODUCT_ID = '2728e8db-cea7-42fd-bdde-ad665eccb5dc';

export function setup() {
    console.log('🎫 Queue Token Generation');
    console.log('==========================');
    console.log(`Product ID: ${PRODUCT_ID}`);
    console.log(`Test Users: 1~${TEST_USERS}`);
    console.log('');

    const health = http.get(`${QUEUE_SERVICE}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Queue Service is not healthy: ${health.status}`);
    }
    console.log('✅ Queue Service is healthy');
    console.log('');

    return { productId: PRODUCT_ID };
}

export default function(data) {
    const userId = __VU;  // ✅ VU 번호 = User ID (1~100)

    const queueRes = http.post(
        `${QUEUE_SERVICE}/api/v1/queues/enter`,
        JSON.stringify({
            productId: data.productId
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId.toString(),
                'X-User-Email': `testuser${userId}@test.com`,
                'X-User-Role': 'USER'
            },
            tags: { name: 'EnterQueue' },
            timeout: '10s',
        }
    );

    const success = check(queueRes, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'has token': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.token;
            } catch (e) {
                return false;
            }
        },
    });

    if (success) {
        try {
            const body = JSON.parse(queueRes.body);
            const queueToken = body.data.token;
            console.log(`✅ User ${userId}: ${queueToken}`);
        } catch (e) {
            console.error(`❌ User ${userId}: Failed to parse response`);
        }
    } else {
        console.error(`❌ User ${userId}: status=${queueRes.status}, body=${queueRes.body}`);
    }
}

export function teardown(data) {
    console.log('');
    console.log('🏁 Token Generation Completed');
    console.log('Expected: 100 tokens for User 1~100');
}
