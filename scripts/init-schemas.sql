-- 모든 스키마 생성 (처음 실행 시에만)
CREATE SCHEMA IF NOT EXISTS auth_schema;
CREATE SCHEMA IF NOT EXISTS user_schema;
CREATE SCHEMA IF NOT EXISTS order_schema;
CREATE SCHEMA IF NOT EXISTS payment_schema;
CREATE SCHEMA IF NOT EXISTS product_schema;
CREATE SCHEMA IF NOT EXISTS queue_schema;
CREATE SCHEMA IF NOT EXISTS time_deal_schema;

-- 권한 부여
GRANT ALL PRIVILEGES ON SCHEMA auth_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA user_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA order_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA payment_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA product_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA queue_schema TO rushdeal;
GRANT ALL PRIVILEGES ON SCHEMA time_deal_schema TO rushdeal;
