# 🧪 RushDeal 부하테스트 실행 가이드

> **100명 동시 주문 테스트 단계별 실행 매뉴얼**

---

## 📑 목차

1. [사전 준비](#1-사전-준비)
2. [인프라 구성](#2-인프라-구성)
3. [테스트 데이터 생성](#3-테스트-데이터-생성)
4. [상품 및 재고 설정](#4-상품-및-재고-설정)
5. [큐 토큰 발급](#5-큐-토큰-발급)
6. [부하테스트 실행](#6-부하테스트-실행)
7. [결과 검증](#7-결과-검증)
8. [문제 해결](#8-문제-해결)

---

## 1. 사전 준비

### 1.1 필수 소프트웨어 설치

| 도구 | 버전 | 설치 확인 명령어 | 용도 |
|------|------|------------------|------|
| Docker Desktop | 최신 | `docker --version` | 컨테이너 실행 |
| WSL2 | Ubuntu 24.04 | `wsl --version` | Linux 환경 |
| k6 | 최신 | `k6 version` | 부하테스트 |
| jq | 최신 | `jq --version` | JSON 파싱 |
| curl | 내장 | `curl --version` | HTTP 요청 |

### 1.2 k6 설치 (WSL에서 실행)

```bash
# GPG 키 추가
sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69

# 저장소 추가
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list

# 설치
sudo apt-get update
sudo apt-get install k6

# 확인
k6 version
```

### 1.3 프로젝트 경로 이동

```bash
# Windows PowerShell에서 WSL 실행
wsl

# 프로젝트 디렉토리로 이동
cd /mnt/c/Users/<사용자명>/IdeaProjects/sparta/project/fork/rush-deal
```

---

## 2. 인프라 구성

### 2.1 Docker Compose 실행

```bash
# 컨테이너 시작
docker-compose up -d

# 상태 확인
docker-compose ps
```

**예상 출력:**
```
NAME                        STATUS
rushdeal_postgres          Up (healthy)
rushdeal_kafka             Up (healthy)
rushdeal_zookeeper         Up
rushdeal_*_redis (6개)     Up (healthy)
```

### 2.2 인프라 헬스체크

**스크립트 생성:** `check-infrastructure.sh`

```bash
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
```

**실행:**
```bash
chmod +x check-infrastructure.sh
./check-infrastructure.sh
```

### 2.3 Kafka 토픽 생성

**스크립트 생성:** `create-kafka-topics.sh`

```bash
#!/bin/bash
# create-kafka-topics.sh

echo "📋 Creating Kafka topics..."

TOPICS=(
  "stock.reserved"
  "stock.reservation.failed"
  "stock.reservation.requested"
  "order.created"
  "order.paid"
  "order.cancelled"
  "order.updated"
  "order.refunded"
  "order.purchase.confirmed"
  "payment.completed"
  "payment.cancelled"
  "payment.refund.requested"
  "point.earn.requested"
  "point.refund.requested"
  "order-complete-token-remove"
)

for topic in "${TOPICS[@]}"; do
  docker exec rushdeal_kafka kafka-topics --create \
    --topic "$topic" \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists 2>/dev/null
  
  if [ $? -eq 0 ]; then
    echo "  ✅ $topic"
  else
    echo "  ⚠️  $topic (already exists)"
  fi
done

echo ""
echo "Created topics:"
docker exec rushdeal_kafka kafka-topics --list --bootstrap-server localhost:9092
```

**실행:**
```bash
chmod +x create-kafka-topics.sh
./create-kafka-topics.sh
```

---

## 3. 테스트 데이터 생성

### 3.1 사용자 및 포인트 데이터

**스크립트 생성:** `test-data.sql`

```sql
\echo '============================================'
\echo '🔧 RushDeal User & Point Test Data Init START'
\echo '============================================'

-- =====================================================
-- 1. USERS (USER ROLE 100명)
-- =====================================================
\echo ''
\echo '👤 [1/3] Creating test users (USER x100)...'

DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO user_schema.p_user (
            email,
            password,
            name,
            role,
            created_at,
            updated_at
        )
        VALUES (
            'testuser' || i || '@test.com',
            '$2a$10$Wpwa3/z.SSzV.sgnWpunQOptydcMZfV6ufFMvJ8kOErVa8oVw15nW', -- pass1234!
            '테스트유저' || i,
            'USER',
            now(),
            now()
        )
        ON CONFLICT (email) DO NOTHING;
    END LOOP;
END $$;

\echo '✅ USER 100명 생성 완료'

-- =====================================================
-- 2. SELLER
-- =====================================================
\echo ''
\echo '🏪 Creating test seller...'

INSERT INTO user_schema.p_user (
    email, password, name, role, created_at, updated_at
)
VALUES (
    'seller@test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qizxLjxmWiGXQxPTGBLxGQXLlNji',
    '타임딜판매자',
    'SELLER',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;

\echo '✅ SELLER 생성 완료'

-- =====================================================
-- 3. MASTER
-- =====================================================
\echo ''
\echo '👑 Creating test master...'

INSERT INTO user_schema.p_user (
    email, password, name, role, created_at, updated_at
)
VALUES (
    'master@test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMye1J7qizxLjxmWiGXQxPTGBLxGQXLlNji',
    '관리자',
    'MASTER',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;

\echo '✅ MASTER 생성 완료'

-- =====================================================
-- 4. POINT INITIALIZATION (USER 100명 → 10,000 포인트)
-- =====================================================
\echo ''
\echo '💰 [4/4] Initializing points (10,000 per user)...'

INSERT INTO user_schema.p_point_history (
    id, user_id, order_id, amount, balance_after,
    type, saga_id, created_at, confirmed_at
)
SELECT
    gen_random_uuid(),
    u.user_id,
    gen_random_uuid()::text,
    10000,
    10000,
    'EARN_CONFIRM',
    gen_random_uuid()::text,
    now(),
    now()
FROM user_schema.p_user u
WHERE u.role = 'USER'
  AND NOT EXISTS (
    SELECT 1 FROM user_schema.p_point_history ph
    WHERE ph.user_id = u.user_id AND ph.type = 'EARN_CONFIRM'
);

\echo '✅ USER 100명 포인트 10,000 지급 완료'

-- =====================================================
-- SUMMARY
-- =====================================================
\echo ''
\echo '📊 User Summary'
SELECT role, COUNT(*) FROM user_schema.p_user GROUP BY role;

\echo ''
\echo '📊 Point Summary'
SELECT
    COUNT(DISTINCT user_id) AS users_with_point,
    SUM(balance_after) AS total_points
FROM user_schema.p_point_history
WHERE type = 'EARN_CONFIRM';

\echo ''
\echo '============================================'
\echo '🎉 User & Point Test Data Init COMPLETED'
\echo '============================================'
```

**실행:**
```bash
docker exec -i rushdeal_postgres psql -U rushdeal -d rushdeal < test-data.sql
```

### 3.2 데이터 검증

```bash
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT '👥 Test Users: ' || COUNT(*) AS test_users 
FROM user_schema.p_user 
WHERE role = 'USER';
"

docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
SELECT '💰 Total Points: ' || SUM(balance_after) AS total_points 
FROM user_schema.p_point_history 
WHERE type = 'EARN_CONFIRM';
"
```

**예상 출력:**
```
test_users: 100
total_points: 1000000
```

---

## 4. 상품 및 재고 설정

### 4.1 로컬 IP 확인

```bash
# Windows에서 실행
ipconfig | findstr IPv4
```

**예시:** `172.30.1.100`

### 4.2 상품 생성 (Postman)

**요청:**
```http
POST http://localhost:8020/api/v1/products
Content-Type: application/json
X-User-Id: 102
X-User-Email: master@test.com
X-User-Role: MASTER

{
  "sellerId": 101,
  "companyName": "나이키코리아",
  "productName": "후드집업",
  "description": "우먼스 기모 후드집업",
  "price": 119000,
  "category": "CLOTHES",
  "optionRequests": [
    {"size": "S", "color": "빨강"},
    {"size": "S", "color": "파랑"},
    {"size": "M", "color": "빨강"},
    {"size": "M", "color": "파랑"}
  ]
}
```

**응답에서 `productId` 저장**

### 4.3 타임딜 생성 (Postman)

**요청:**
```http
POST http://localhost:8030/api/v1/timedeals
Content-Type: application/json
X-User-Id: 102
X-User-Email: master@test.com
X-User-Role: MASTER

{
  "title": "나이키 타임딜",
  "description": "20% 할인가 진행",
  "discountPrice": 95200,
  "limitQuantity": 5,
  "startAt": "2026-01-07T02:15:00Z",
  "endAt": "2026-12-30T23:59:59Z",
  "status": "IN_PROGRESS",
  "productId": "{{productId}}"
}
```

**응답에서 `timeDealId` 및 4개 `timeDealProductId` 저장**

### 4.4 재고 생성 (4개 타임딜 상품 반복)

**요청:**
```http
POST http://localhost:8030/api/v1/stocks
Content-Type: application/json
X-User-Id: 102
X-User-Email: master@test.com
X-User-Role: MASTER

{
  "productId": "{{timeDealProductId}}",
  "totalStock": 100
}
```

**각 옵션별로 생성된 타임딜상품ID로 실행하여 4개 `timeDealStockId` 저장**

### 4.5 ID 자동 조회

**스크립트 생성:** `get-test-ids.sh`

```bash
#!/bin/bash
# get-test-ids.sh

echo "📋 Test Data IDs"
echo "================"
echo ""

# Product ID
echo "🛍️ Product ID:"
PRODUCT_ID=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -c \
  "SELECT id FROM product_schema.p_product WHERE product_name LIKE '%후드집업%' LIMIT 1;" | xargs)
echo "$PRODUCT_ID"

# TimeDeal ID
echo ""
echo "⏰ TimeDeal ID:"
TIMEDEAL_ID=$(docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -c \
  "SELECT id FROM time_deal_schema.p_time_deal WHERE title LIKE '%나이키%' LIMIT 1;" | xargs)
echo "$TIMEDEAL_ID"

# TimeDeal Product IDs
echo ""
echo "📦 TimeDeal Product IDs:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT id FROM time_deal_schema.p_time_deal_product 
   WHERE time_deal_id = '$TIMEDEAL_ID' ORDER BY created_at;"

# Stock IDs
echo ""
echo "📦 Stock IDs:"
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -t -A -c \
  "SELECT s.id FROM time_deal_schema.p_time_deal_stock s
   JOIN time_deal_schema.p_time_deal_product tdp ON s.time_deal_product_id = tdp.id
   WHERE tdp.time_deal_id = '$TIMEDEAL_ID'
   ORDER BY s.created_at;"

echo ""
echo "✅ Done!"
```

**실행:**
```bash
chmod +x get-test-ids.sh
./get-test-ids.sh
```

### 4.6 대기열 정책 생성 (Postman)

**요청:**
```http
POST http://localhost:8040/api/v1/queue/policies
Content-Type: application/json
X-User-Id: 102
X-User-Role: MASTER

{
  "productId": "{{productId}}",
  "dealName": "신규 타임딜 이벤트",
  "status": "RUNNING",
  "startTime": "2026-01-01T00:00:00",
  "endTime": "2026-12-30T23:59:59",
  "maxCapacity": 10000,
  "limitSize": 100,
  "queueGap": 2,
  "ttl": 36000
}
```

---

## 5. 큐 토큰 발급

### 5.1 토큰 발급 스크립트

**파일 생성:** `generate-queue-tokens.js`

⚠️ **중요:** 파일 내 `PRODUCT_ID`와 `QUEUE_SERVICE` IP 주소 수정

```javascript
// generate-queue-tokens.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 100,
    iterations: 100,
    thresholds: {
        'http_req_duration': ['p(95)<5000'],
        'checks': ['rate>0.90'],
    },
};

const QUEUE_SERVICE = 'http://172.30.1.100:8040'; // ⚠️ 실제 IP로 수정
const TEST_USERS = 100;
const PRODUCT_ID = 'YOUR_PRODUCT_ID_HERE'; // ⚠️ 실제 ID로 수정

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
    const userId = ((__VU - 1) % TEST_USERS) + 1;

    const queueRes = http.post(
        `${QUEUE_SERVICE}/api/v1/queues/enter`,
        JSON.stringify({ productId: data.productId }),
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
        console.error(`❌ User ${userId}: status=${queueRes.status}`);
    }
}

export function teardown(data) {
    console.log('');
    console.log('🏁 Token Generation Completed');
}
```

### 5.2 토큰 발급 실행

```bash
# 토큰 발급 및 로그 저장
k6 run generate-queue-tokens.js 2>&1 | tee queue-tokens-output.log

# 토큰만 추출
grep "✅ User" queue-tokens-output.log | \
  grep -oE '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}' > tokens.txt

# 확인
echo "생성된 토큰 수: $(wc -l < tokens.txt)"
head -5 tokens.txt
```

**예상 출력:**
```
생성된 토큰 수: 100
```

---

## 6. 부하테스트 실행

### 6.1 테스트 스크립트

**파일 생성:** `load-test-order.js`

⚠️ **중요:**
- `TEST_DATA` 내 ID들을 `./get-test-ids.sh` 결과로 수정
- `ORDER_SERVICE` IP 주소 수정

```javascript
// load-test-order.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 큐 토큰 로드
const queueTokens = new SharedArray('tokens', function() {
    const data = open('./tokens.txt').split('\n').filter(t => t.trim());
    console.log(`✅ Loaded ${data.length} queue tokens`);
    return data;
});

// 커스텀 메트릭
const orderSuccessRate = new Rate('order_success_rate');
const orderFailureRate = new Rate('order_failure_rate');
const orderDuration = new Trend('order_duration_ms');
const stockErrors = new Counter('stock_errors');
const pointErrors = new Counter('point_errors');
const queueErrors = new Counter('queue_errors');
const purchaseLimitErrors = new Counter('purchase_limit_errors');
const timeDealErrors = new Counter('timedeal_errors');
const sagaFailures = new Counter('saga_failures');
const pendingOrders = new Counter('pending_orders_created');
const totalOrderQuantity = new Counter('total_order_quantity');

export const options = {
    scenarios: {
        order_rush: {
            executor: 'per-vu-iterations',
            vus: 100,
            iterations: 1,
            maxDuration: '2m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<5000', 'p(99)<10000'],
        'http_req_failed': ['rate<0.5'],
        'order_success_rate': ['rate>0.5'],
        'order_duration_ms': ['p(95)<8000'],
    },
};

const TEST_USERS = 100;

// ⚠️ ./get-test-ids.sh 실행 결과로 교체
const TEST_DATA = {
    productId: 'YOUR_PRODUCT_ID',
    timeDealId: 'YOUR_TIMEDEAL_ID',
    stockIds: [
        'STOCK_ID_1',
        'STOCK_ID_2',
        'STOCK_ID_3',
        'STOCK_ID_4',
    ],
    productPrice: 95200,
    pointToUse: 1000,
    purchaseLimit: 5,
};

const ORDER_SERVICE = 'http://172.30.1.100:8050'; // ⚠️ 실제 IP로 수정

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

    const orderHealth = http.get(`${ORDER_SERVICE}/actuator/health`, { timeout: '10s' });
    if (orderHealth.status !== 200) {
        throw new Error(`Order Service is not healthy: ${orderHealth.status}`);
    }
    console.log('✅ Order Service is healthy');
    console.log('');

    if (queueTokens.length < 100) {
        throw new Error(`Not enough tokens: ${queueTokens.length} < 100`);
    }

    console.log(`✅ Ready: 100 users will place orders simultaneously`);
    console.log(`   - 약 70%는 정상 주문 (1~5개)`);
    console.log(`   - 약 30%는 제한 초과 테스트 (6~9개)`);
    console.log('');

    return { testData: TEST_DATA };
}

export default function(data) {
    const testData = data.testData;
    const userId = __VU;
    const queueToken = queueTokens[userId - 1];

    if (!queueToken) {
        console.error(`❌ [User ${userId}] No queue token available`);
        return;
    }

    // 주문 아이템 생성 (70% 정상, 30% 제한 초과)
    const shouldTestLimit = Math.random() < 0.3;
    let orderItems = [];

    if (shouldTestLimit) {
        // 구매 제한 초과 (6~9개)
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
        // 정상 주문 (1~5개)
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

        // 총 수량 조정
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
        totalOrderQuantity.add(totalQuantity);

        const itemsDesc = orderItems.map(item =>
            `Stock${testData.stockIds.indexOf(item.timeDealStockId) + 1}×${item.quantity}`
        ).join(', ');

        const badge = totalQuantity > testData.purchaseLimit ? '🎯' : '✅';
        console.log(`${badge} [User ${userId}] Order created - Saga: ${sagaId.substring(0, 8)}..., Items: [${itemsDesc}], Total: ${totalQuantity}, Duration: ${orderDurationMs}ms`);
    } else {
        classifyAndLogError(userId, orderRes, orderItems, totalQuantity, testData);
    }
}

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
```
