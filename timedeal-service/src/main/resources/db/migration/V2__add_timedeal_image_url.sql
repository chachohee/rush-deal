-- p_time_deal 테이블에 image_url 컬럼 추가 (product의 이미지를 denormalize)
ALTER TABLE time_deal_schema.p_time_deal
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1024);
