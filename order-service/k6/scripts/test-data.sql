\echo '============================================'
\echo '🔧 RushDeal User & Point Test Data Init START'
\echo '============================================'

-- =====================================================
-- 1. USERS (USER ROLE 1000명)
-- =====================================================
\echo ''
\echo '👤 [1/3] Creating test users (USER x1000)...'

DO $$
DECLARE
i INT;
BEGIN
FOR i IN 1..1000 LOOP
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

\echo '✅ USER 1000명 생성 완료'

-- =====================================================
-- 2. SELLER
-- =====================================================
\echo ''
\echo '🏪 Creating test seller...'

INSERT INTO user_schema.p_user (
    email,
    password,
    name,
    role,
    created_at,
    updated_at
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
\echo '👑 Creating master user...'

INSERT INTO user_schema.p_user (
    email,
    password,
    name,
    role,
    created_at,
    updated_at
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
-- 4. POINT INITIALIZATION (USER 1000명 → 10,000 포인트)
-- =====================================================
\echo ''
\echo '💰 [3/3] Initializing points (10,000 per user)...'

INSERT INTO user_schema.p_point_history (
    id,
    user_id,
    order_id,
    amount,
    balance_after,
    type,
    saga_id,
    created_at,
    confirmed_at
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
    SELECT 1
    FROM user_schema.p_point_history ph
    WHERE ph.user_id = u.user_id
      AND ph.type = 'EARN_CONFIRM'
);

\echo '✅ USER 1000명 포인트 10,000 지급 완료'

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
    SUM(balance_after)      AS total_points
FROM user_schema.p_point_history
WHERE type = 'EARN_CONFIRM';

\echo ''
\echo '============================================'
\echo '🎉 User & Point Test Data Init COMPLETED'
\echo '============================================'
