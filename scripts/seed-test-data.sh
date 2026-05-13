#!/usr/bin/env bash
# ============================================
# RushDeal 테스트 시드 데이터 생성 스크립트
# ============================================
# 사용법: ./scripts/seed-test-data.sh
#
# 생성되는 데이터:
#   - 계정 3종: master / seller / user (각각 pass1234!)
#   - 상품 5개 (셀러 보유)
#   - 타임딜 5개: SCHEDULED 2, IN_PROGRESS 2, ENDED 1
#   - 각 타임딜마다 재고 100개
#   - user 계정에 포인트 100,000 충전 + 기본 배송지
# ============================================
set -euo pipefail

API="${API:-http://localhost:8080}"
PG="docker exec rushdeal_postgres psql -U rushdeal -d rushdeal"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq가 설치되어 있지 않습니다. brew install jq 로 설치해주세요." >&2
  exit 1
fi

step() { echo -e "\n\033[1;34m▶ $1\033[0m"; }
ok()   { echo -e "  \033[32m✓ $1\033[0m"; }
warn() { echo -e "  \033[33m! $1\033[0m"; }

# ============================================
# 1) 계정 생성
# ============================================
step "1. 계정 생성 (master / seller / user)"
for spec in \
  'master@rushdeal.com 관리자 MASTER' \
  'seller@rushdeal.com 판매자 SELLER' \
  'user@rushdeal.com   사용자 USER'; do
  read -r email name role <<<"$spec"
  curl -sf -X POST "$API/api/v1/auth/signup" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"pass1234!\",\"name\":\"$name\",\"role\":\"$role\"}" \
    >/dev/null 2>&1 || true
  ok "$email ($role)"
done

# ============================================
# 2) 로그인
# ============================================
step "2. 로그인 후 토큰 발급"
login() {
  curl -sf -X POST "$API/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"pass1234!\"}" | jq -r '.accessToken'
}
MASTER_TOKEN=$(login master@rushdeal.com)
SELLER_TOKEN=$(login seller@rushdeal.com)
USER_TOKEN=$(login user@rushdeal.com)
USER_ID=$($PG -t -A -c "SELECT user_id FROM user_schema.p_user WHERE email='user@rushdeal.com';")
ok "토큰 확보 · USER_ID=$USER_ID"

# ============================================
# 3) 배송지
# ============================================
step "3. user 배송지 등록"
curl -sf -X POST "$API/api/v1/users/me/addresses" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"recipientName":"사용자","recipientPhone":"01012345678","zipCode":"06234","addressBase":"서울특별시 강남구 테헤란로 123","addressDetail":"4층 401호","deliveryMessage":"문 앞에 놓아주세요"}' \
  >/dev/null
ok "기본 배송지 등록 완료"

# ============================================
# 4) 포인트 100,000
# ============================================
step "4. user 포인트 100,000 적립"
$PG -c "
INSERT INTO user_schema.p_point_history
  (id, user_id, type, amount, balance_after, created_at, confirmed_at)
VALUES
  (gen_random_uuid(), $USER_ID, 'EARN', 100000, 100000, NOW(), NOW());
" >/dev/null
ok "포인트 100,000 적립"

# ============================================
# 5) 상품 5개 등록 (셀러)
# ============================================
step "5. 상품 5개 등록"
declare -a PRODUCT_IDS=()
CATEGORIES=(CLOTHES SHOES BAG HEADWEAR ACCESSORY)
NAMES=("기본 티셔츠" "러닝화" "토트백" "비니" "체인 목걸이")
PRICES=(29000 89000 49000 19000 35000)
for i in 0 1 2 3 4; do
  body=$(jq -nc \
    --arg name "${NAMES[$i]}" \
    --arg desc "${NAMES[$i]} 상품 설명입니다." \
    --argjson price "${PRICES[$i]}" \
    --arg cat "${CATEGORIES[$i]}" \
    '{companyName:"러시딜브랜드", productName:$name, description:$desc, price:$price, category:$cat,
      optionRequests:[{size:"S",color:"BLACK"},{size:"M",color:"BLACK"},{size:"L",color:"BLACK"}]}')
  pid=$(curl -sf -X POST "$API/api/v1/products" \
    -H "Authorization: Bearer $SELLER_TOKEN" \
    -H "Content-Type: application/json" -d "$body" | jq -r '.productId')
  PRODUCT_IDS+=("$pid")
  ok "${NAMES[$i]} → $pid"
done

# ============================================
# 6) 타임딜 5개 등록
# ============================================
step "6. 타임딜 5개 등록 (모두 SCHEDULED 로 등록)"
declare -a TIMEDEAL_IDS=()
iso() { date -u -v+"${1}M" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -d "+$1 minutes" '+%Y-%m-%dT%H:%M:%SZ'; }

create_timedeal() {
  local pid=$1 title=$2 desc=$3 disc=$4 startMin=$5 endMin=$6
  curl -sf -X POST "$API/api/v1/timedeals" \
    -H "Authorization: Bearer $SELLER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$(jq -nc \
      --arg t "$title" --arg d "$desc" --argjson p "$disc" \
      --arg s "$(iso "$startMin")" --arg e "$(iso "$endMin")" \
      --arg pid "$pid" \
      '{title:$t, description:$d, discountPrice:$p, limitQuantity:5, startAt:$s, endAt:$e, status:"SCHEDULED", productId:$pid}')" \
    | jq -r '.timeDealId // .id'
}

T0=$(create_timedeal "${PRODUCT_IDS[0]}" "[예정] 기본 티셔츠 핫딜"     "내일 시작합니다"    19000 1440 2880)
T1=$(create_timedeal "${PRODUCT_IDS[1]}" "[예정] 러닝화 50% 세일"      "특가 행사"          44500  720 1440)
T2=$(create_timedeal "${PRODUCT_IDS[2]}" "[진행중] 토트백 30% 할인"    "지금 바로!"         34000   60 1440)
T3=$(create_timedeal "${PRODUCT_IDS[3]}" "[진행중] 비니 한정 특가"     "5,000원"            5000    60 1440)
T4=$(create_timedeal "${PRODUCT_IDS[4]}" "[마감] 체인 목걸이 종료"     "이미 끝났습니다"    25000   60 1440)
TIMEDEAL_IDS=("$T0" "$T1" "$T2" "$T3" "$T4")
for tid in "${TIMEDEAL_IDS[@]}"; do ok "$tid"; done

# ============================================
# 7) 타임딜별 TimeDealProduct ID 조회 → 재고 등록 (master)
# ============================================
step "7. 재고 등록 (각 100개)"
for tid in "${TIMEDEAL_IDS[@]}"; do
  tdp_id=$(curl -sf "$API/api/v1/timedeals/$tid" \
    -H "Authorization: Bearer $SELLER_TOKEN" \
    | jq -r '.timeDealProdutResultList[0].id')
  curl -sf -X POST "$API/api/v1/stocks" \
    -H "Authorization: Bearer $MASTER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"$tdp_id\",\"totalStock\":100}" >/dev/null
  ok "TimeDealProduct $tdp_id ← 100개"
done

# ============================================
# 8) 상태 강제 전환 (DB 직접 수정 — @Future 검증 우회)
# ============================================
step "8. 상태 강제 전환"

for tid in "$T2" "$T3"; do
  $PG -c "
    UPDATE time_deal_schema.p_time_deal
       SET status='IN_PROGRESS',
           start_at = NOW() - INTERVAL '5 minutes',
           end_at   = NOW() + INTERVAL '24 hours'
     WHERE id = '$tid';
  " >/dev/null
done
ok "IN_PROGRESS 2개 ($T2, $T3)"

$PG -c "
  UPDATE time_deal_schema.p_time_deal
     SET status='ENDED',
         start_at = NOW() - INTERVAL '2 hours',
         end_at   = NOW() - INTERVAL '1 hour'
   WHERE id = '$T4';
" >/dev/null
ok "ENDED 1개 ($T4)"

# ============================================
# 요약
# ============================================
echo -e "\n\033[1;32m=========================="
echo "✅ 시드 데이터 준비 완료"
echo "==========================\033[0m"
echo
echo "계정:"
echo "  master@rushdeal.com / pass1234!"
echo "  seller@rushdeal.com / pass1234!"
echo "  user@rushdeal.com   / pass1234!  (포인트 100,000P · 배송지 등록됨)"
echo
echo "상품 5개 · 타임딜 5개 (예정 2 / 진행중 2 / 마감 1) · 각 타임딜 재고 100개"
echo
echo "다음 단계:"
echo "  1) 프론트(rush-deal-web)에서 user 로 로그인"
echo "  2) /timedeals 의 '진행중' 탭에서 카드 클릭"
echo "  3) 대기열 진입 → ACTIVE 되면 주문 → 결제"
echo
echo "전체 흐름: docs/TESTING_GUIDE.md"
