# RushDeal 전체 흐름 테스트 가이드

관리자 / 판매자 / 사용자 시나리오로 회원가입 → 상품 등록 → 타임딜 운영 → 사용자 주문/결제까지 한 번에 검증하는 가이드입니다.

> **전제:** `./start-local.sh` 로 모든 서비스가 가동 중이어야 합니다. 게이트웨이 헬스체크가 200이 될 때까지 기다리세요.
> ```bash
> curl -s http://localhost:8080/actuator/health | jq .status
> ```

---

## 0. 한 번에 시드 데이터 만들기

```bash
./scripts/seed-test-data.sh
```

스크립트가 자동으로 다음을 생성합니다.

| 항목 | 내용 |
|------|------|
| 계정 | `master@rushdeal.com` / `seller@rushdeal.com` / `user@rushdeal.com` (비밀번호 공통 `pass1234!`) |
| 상품 | 5개 (사이즈 S/M/L 옵션 각각) |
| 재고 | 상품당 100개 |
| 타임딜 | SCHEDULED 2, IN_PROGRESS 2, ENDED 1 |
| 사용자 부가 데이터 | 포인트 100,000 P · 기본 배송지 |

> 스크립트는 멱등성을 가지지 않으니, 다시 실행하기 전에는 DB 를 초기화하거나 `docker-compose down -v` 로 볼륨까지 지워주세요.

---

## 1. 역할별 수동 흐름

### 1-A. 관리자 (MASTER)

```bash
# 회원가입 (이미 했다면 스킵)
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"master@rushdeal.com","password":"pass1234!","name":"관리자","role":"MASTER"}'

# 로그인 → accessToken 획득
MASTER=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"master@rushdeal.com","password":"pass1234!"}' | jq -r .accessToken)
```

권한: 상품/타임딜/재고/감사 로그 모두 접근. UI 는 `/admin` 메뉴.

### 1-B. 판매자 (SELLER)

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@rushdeal.com","password":"pass1234!","name":"판매자","role":"SELLER"}'

SELLER=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@rushdeal.com","password":"pass1234!"}' | jq -r .accessToken)
```

#### 상품 등록 (이미지 선택)

```bash
# (선택) 이미지 업로드 — multipart, 반환된 imageUrl 을 다음 요청에 넣는다
IMG=$(curl -s -X POST http://localhost:8080/api/v1/products/images \
  -H "Authorization: Bearer $SELLER" \
  -F "file=@/path/to/photo.jpg" | jq -r .imageUrl)

curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $SELLER" \
  -H "Content-Type: application/json" \
  -d "{
    \"companyName\": \"러시딜브랜드\",
    \"productName\": \"기본 티셔츠\",
    \"description\": \"부드러운 면 100%\",
    \"price\": 29000,
    \"category\": \"CLOTHES\",
    \"imageUrl\": \"$IMG\",
    \"optionRequests\": [
      {\"size\":\"S\",\"color\":\"BLACK\"},
      {\"size\":\"M\",\"color\":\"BLACK\"},
      {\"size\":\"L\",\"color\":\"BLACK\"}
    ]
  }"
```

#### 타임딜 등록 (SCHEDULED 상태로 만들고 시간으로 자동 전환)

```bash
# 1시간 후 시작 / 24시간 후 종료
START=$(date -u -v+60M '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -d '+60 minutes' '+%Y-%m-%dT%H:%M:%SZ')
END=$(date -u -v+1440M '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -d '+1440 minutes' '+%Y-%m-%dT%H:%M:%SZ')

curl -X POST http://localhost:8080/api/v1/timedeals \
  -H "Authorization: Bearer $SELLER" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"기본 티셔츠 핫딜\",
    \"description\": \"한정 수량 30% 할인\",
    \"discountPrice\": 19000,
    \"limitQuantity\": 5,
    \"startAt\": \"$START\",
    \"endAt\": \"$END\",
    \"status\": \"SCHEDULED\",
    \"productId\": \"<위에서 받은 productId>\"
  }"
```

> 타임딜은 **시작 시각이 미래**여야만 등록할 수 있습니다 (`@Future` 검증). 즉시 IN_PROGRESS 인 타임딜을 만들어 테스트하려면 시드 스크립트가 하는 것처럼 DB 의 `start_at` / `status` 를 직접 수정해야 합니다.

#### 재고 등록 (현재 MASTER 권한 필요)

```bash
curl -X POST http://localhost:8080/api/v1/stocks \
  -H "Authorization: Bearer $MASTER" \
  -H "Content-Type: application/json" \
  -d '{"productId":"<상품 ID>","totalStock":100}'
```

#### 셀러 대시보드

브라우저: `http://localhost:3000/seller`
- 진행 중 / 예정 / 마감 타임딜 KPI
- 누적 / 7일 매출·주문
- 재고 부족 알림 (10개 미만 자동)
- 최근 등록 타임딜·상품 5개

### 1-C. 사용자 (USER)

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"user@rushdeal.com","password":"pass1234!","name":"사용자","role":"USER"}'

USER=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@rushdeal.com","password":"pass1234!"}' | jq -r .accessToken)
```

#### 배송지 등록

```bash
curl -X POST http://localhost:8080/api/v1/users/me/addresses \
  -H "Authorization: Bearer $USER" \
  -H "Content-Type: application/json" \
  -d '{
    "recipientName":"사용자",
    "recipientPhone":"01012345678",
    "zipCode":"06234",
    "addressBase":"서울특별시 강남구 테헤란로 123",
    "addressDetail":"4층 401호",
    "deliveryMessage":"문 앞에 놓아주세요"
  }'
```

#### 포인트 충전

현재 외부 결제 충전 API 는 노출되어 있지 않습니다. 테스트 환경에서는 시드 스크립트가 DB 에 직접 적립합니다.

```sql
-- 포인트 잔액은 p_point_history 의 latest balance_after
INSERT INTO user_schema.p_point_history
  (id, user_id, type, amount, balance_after, created_at, confirmed_at)
VALUES
  (gen_random_uuid(), <userId>, 'EARN', 100000, 100000, NOW(), NOW());
```

---

## 2. 사용자 주문 & 결제 흐름

진행중 타임딜이 1개 이상 있다고 가정합니다 (시드 데이터로 자동 생성).

### 2-A. 브라우저 흐름 (권장)

1. `http://localhost:3000` 에서 `user@rushdeal.com` 로그인
2. 상단의 **TIMEDEAL** 클릭 → "진행중" 탭에서 카드 선택
3. 상세 페이지에서 **대기열 진입** 클릭
4. 순위가 "활성화 (ACTIVE)" 로 변경되면 수량 / 포인트 입력
5. **주문** 버튼 클릭 → 주문 접수 화면
6. (실 결제 연동을 활성화한 경우) PortOne 결제창으로 리다이렉트, 결제 완료 후 상태 PAID 로 전환
7. `/orders` 에서 주문 내역 / 상태 확인

### 2-B. API 흐름 (curl)

```bash
TIMEDEAL_ID=<진행중 타임딜 ID>
PRODUCT_ID=<해당 타임딜의 상품 ID>

# (1) 대기열 진입 → tokenId 발급
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/queues/enter \
  -H "Authorization: Bearer $USER" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\"}" | jq -r '.data.tokenId // .tokenId')

# (2) 순위 조회 — status 가 ACTIVE 가 될 때까지 폴링 (스케줄러가 활성화)
curl -s "http://localhost:8080/api/v1/queues/rank?productId=$PRODUCT_ID" \
  -H "Authorization: Bearer $USER" \
  -H "X-Queue-Token: $TOKEN"

# (3) ACTIVE 상태가 되면 — TimeDealDetail 의 timeDealProdutResultList[0].id 가 timeDealStockId
STOCK_ID=$(curl -s "http://localhost:8080/api/v1/timedeals/$TIMEDEAL_ID" \
  -H "Authorization: Bearer $USER" | jq -r '.timeDealProdutResultList[0].id')

# (4) 주문 생성 (포인트 19000 전액 사용 예시)
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $USER" \
  -H "Content-Type: application/json" \
  -H "X-Queue-Token: $TOKEN" \
  -d "{
    \"timeDealId\": \"$TIMEDEAL_ID\",
    \"productId\": \"$PRODUCT_ID\",
    \"orderItems\": [{\"timeDealStockId\":\"$STOCK_ID\",\"quantity\":1}],
    \"pointUsed\": 19000,
    \"shippingInfo\": {
      \"recipientName\":\"사용자\",
      \"recipientPhone\":\"01012345678\",
      \"zipCode\":\"06234\",
      \"addressBase\":\"서울특별시 강남구 테헤란로 123\",
      \"addressDetail\":\"4층 401호\",
      \"deliveryMessage\":\"문 앞에 놓아주세요\"
    }
  }"
```

응답: `{ orderId, status: "PENDING", ... }`

### 2-C. 결제 단계

```bash
ORDER_ID=<위에서 받은 orderId>

# (5) 결제 시작 — PortOne 결제창 정보 반환
curl -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/payment" \
  -H "Authorization: Bearer $USER"

# (6) 결제 성공 시 PortOne 의 웹훅이 payment-service 로 전송됨
#     → order 상태가 PAID 로 업데이트
#     실제 결제 없이 테스트하려면 ORDER 상태를 직접 갱신:
docker exec rushdeal_postgres psql -U rushdeal -d rushdeal -c "
  UPDATE order_schema.p_order
     SET status='PAID', payment_completed_at = NOW()
   WHERE order_id = '$ORDER_ID';
"
```

> PortOne 시크릿이 비어 있으면 결제 단계가 실패합니다. `.env` 의 `PORTONE_*` 값을 채우거나 위 SQL 로 우회하세요.

### 2-D. 구매 확정

```bash
# (7) 사용자가 직접 확정 (또는 일정 기간 후 자동 확정)
curl -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/confirm" \
  -H "Authorization: Bearer $USER"
```

---

## 3. 시나리오별 검증 포인트

| 시나리오 | 확인 위치 |
|----------|-----------|
| 타임딜 상태 자동 전환 | `/timedeals` 에 진행중/예정/마감 카드 노출, 셀러 대시보드 KPI 카운트 |
| 검색 (Elasticsearch + nori) | `/search` 에서 한글 키워드 입력 → 토큰화된 결과 |
| 관심 등록 | 카드의 ♡ 클릭 → `/interested` 에서 확인 |
| 알림 | 종 아이콘 (WebSocket STOMP, 타임딜 시작 / 주문 상태 변경 등) |
| 재고 차감 | 셀러 대시보드 "재고 부족" 표, 또는 master 의 `/admin/stocks` |
| 셀러 매출 / 주문 집계 | `/seller` 대시보드의 누적 / 7일 카드 |
| 감사 로그 | master 의 `/admin/audits` |

---

## 4. 자주 부딪히는 함정

1. **타임딜 등록 시 `startAt` 검증 오류**: `@Future` 가 적용되어 있어 현재 시각 이후만 허용. 즉시 활성화는 DB 수정으로만 가능합니다.
2. **결제 401/500**: `.env` 의 `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY` 가 비어 있으면 실패합니다. 테스트만 한다면 위 2-C 의 SQL 로 우회하세요.
3. **WebSocket 503**: API Gateway 가 spring-webflux 가 아닌 spring-web 의존성을 받으면 충돌 발생. 현재 빌드는 Netty 로 통일되어 있고, 변경 시 회귀에 주의.
4. **검색 결과 비어 있음**: Elasticsearch 데이터는 타임딜 인덱서가 채웁니다. 컨테이너 재시작 후에는 인덱스가 비어 있을 수 있으니 `/api/v1/timedeals` 가 응답하는지 먼저 확인하고, `/admin/search/reindex` 가 있는 경우 호출.
5. **사용자에 포인트 없음**: 위 SQL 로 직접 적립하거나 시드 스크립트 사용.

---

## 5. 정리 방법

```bash
./stop-local.sh
# 데이터까지 완전 초기화
docker-compose -f docker-compose-app.yml down -v
```

다시 시작 후 `./scripts/seed-test-data.sh` 만 돌리면 위 흐름을 그대로 재현할 수 있습니다.
