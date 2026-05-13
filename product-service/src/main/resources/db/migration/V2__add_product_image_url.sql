-- p_product 테이블에 image_url 컬럼 추가
ALTER TABLE product_schema.p_product
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1024);
