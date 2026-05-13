CREATE TABLE product_schema.p_product (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    category character varying(255) NOT NULL,
    company_name character varying(255) NOT NULL,
    is_active boolean NOT NULL,
    price bigint NOT NULL,
    description character varying(255) NOT NULL,
    product_name character varying(255) NOT NULL,
    seller_id bigint NOT NULL,
    CONSTRAINT p_product_category_check CHECK (((category)::text = ANY ((ARRAY['CLOTHES'::character varying, 'SHOES'::character varying, 'BAG'::character varying, 'HEADWEAR'::character varying, 'ACCESSORY'::character varying, 'UNDERWEAR'::character varying])::text[])))
);
CREATE TABLE product_schema.p_product_option (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    color character varying(255) NOT NULL,
    size character varying(255) NOT NULL,
    product_id uuid NOT NULL
);
ALTER TABLE ONLY product_schema.p_product_option
    ADD CONSTRAINT p_product_option_pkey PRIMARY KEY (id);
ALTER TABLE ONLY product_schema.p_product
    ADD CONSTRAINT p_product_pkey PRIMARY KEY (id);
ALTER TABLE ONLY product_schema.p_product_option
    ADD CONSTRAINT fklm9lo8y687hjypp2qssrb6bsl FOREIGN KEY (product_id) REFERENCES product_schema.p_product(id);
\unrestrict 0mpK8SL2fyKhEMUWqyvrs5yxIpRhIhpzjE6hvlpEEYLGe12IsvWWgw41ka5z3QC
