// generate-queue-tokens.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    scenarios: {
        generate_tokens: {
            executor: 'per-vu-iterations',
            vus: 1000,
            iterations: 1,
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        checks: ['rate>0.95'], // 재시도 로직으로 95% 이상 목표
    },
};

const QUEUE_SERVICE = 'http://172.30.1.72:8040';
const TEST_USERS = 1000;
const PRODUCT_ID = '017b6d7f-9f46-4f75-a039-f0e6c7cf9826';
const MAX_RETRIES = 3; // 최대 재시도 횟수
const RETRY_DELAY = 0.5; // 재시도 대기 시간 (초)

export function setup() {
    console.log('🎫 Queue Token Generation');
    console.log('==========================');
    console.log(`Product ID: ${PRODUCT_ID}`);
    console.log(`Test Users: 1~${TEST_USERS}`);
    console.log(`Max Retries: ${MAX_RETRIES}`);
    console.log('');

    const health = http.get(`${QUEUE_SERVICE}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Queue Service is not healthy: ${health.status}`);
    }
    console.log('✅ Queue Service is healthy');
    console.log('');

    return { productId: PRODUCT_ID };
}

export default function (data) {
    const userId = __VU;
    let retries = MAX_RETRIES;
    let queueRes;
    let success = false;

    // 재시도 로직
    while (retries > 0 && !success) {
        queueRes = http.post(
            `${QUEUE_SERVICE}/api/v1/queues/enter`,
            JSON.stringify({
                productId: data.productId,
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'X-User-Id': userId.toString(),
                    'X-User-Email': `testuser${userId}@test.com`,
                    'X-User-Role': 'USER',
                },
                tags: {
                    name: 'EnterQueue',
                    retry: (MAX_RETRIES - retries + 1).toString() // 몇 번째 시도인지 태깅
                },
                timeout: '10s',
            }
        );

        success = check(queueRes, {
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
            // 성공 시 토큰 출력
            try {
                const body = JSON.parse(queueRes.body);
                const queueToken = body.data.token;
                const attemptInfo = MAX_RETRIES - retries + 1 > 1
                    ? ` (attempt ${MAX_RETRIES - retries + 1})`
                    : '';
                console.log(`✅ User ${userId}${attemptInfo}: ${queueToken}`);
            } catch (e) {
                console.error(`❌ User ${userId}: Failed to parse response`);
            }
            break; // 성공하면 루프 종료
        }

        retries--;

        if (retries > 0) {
            // 재시도 전 대기
            console.log(`⚠️ User ${userId}: Retry ${MAX_RETRIES - retries}/${MAX_RETRIES} after ${RETRY_DELAY}s (status=${queueRes.status})`);
            sleep(RETRY_DELAY);
        } else {
            // 모든 재시도 실패
            console.error(`❌ User ${userId}: All retries failed - status=${queueRes.status}, body=${queueRes.body}`);
        }
    }
}

export function teardown() {
    console.log('');
    console.log('🏁 Token Generation Completed');
    console.log('Expected: 1000 tokens for User 1~1000');
    console.log('Check k6 summary for success rate and retry statistics');
}
