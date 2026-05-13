CREATE TABLE order_schema.p_order (
    order_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    final_amount numeric(12,2) NOT NULL,
    point_used bigint NOT NULL,
    total_amount numeric(12,2) NOT NULL,
    auto_confirm_scheduled_at timestamp(6) with time zone,
    cancelled_at timestamp(6) with time zone,
    ordered_at timestamp(6) with time zone NOT NULL,
    payment_completed_at timestamp(6) with time zone,
    purchase_confirmed_at timestamp(6) with time zone,
    refunded_at timestamp(6) with time zone,
    saga_id uuid,
    address_base character varying(255) NOT NULL,
    address_detail character varying(255) NOT NULL,
    delivery_message character varying(100),
    recipient_name character varying(50) NOT NULL,
    recipient_phone character varying(20) NOT NULL,
    zip_code character varying(10) NOT NULL,
    status character varying(20) NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT p_order_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'PURCHASE_CONFIRMED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying])::text[])))
);
CREATE TABLE order_schema.p_order_history (
    order_history_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    event_type character varying(50) NOT NULL,
    metadata jsonb,
    new_status character varying(20) NOT NULL,
    previous_status character varying(20),
    reason text,
    order_id uuid NOT NULL,
    CONSTRAINT p_order_history_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['ORDER_CREATED'::character varying, 'SHIPPING_INFO_UPDATED'::character varying, 'PAYMENT_COMPLETED'::character varying, 'PURCHASE_CONFIRMED'::character varying, 'CANCELLED_BEFORE_PAYMENT'::character varying, 'REFUNDED'::character varying, 'POINT_USAGE_UPDATED'::character varying, 'PAYMENT_FAILED'::character varying])::text[]))),
    CONSTRAINT p_order_history_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'PURCHASE_CONFIRMED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT p_order_history_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'PURCHASE_CONFIRMED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying])::text[])))
);
CREATE TABLE order_schema.p_order_item (
    order_item_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    discount_price numeric(12,2) NOT NULL,
    product_snapshot jsonb,
    quantity bigint NOT NULL,
    subtotal numeric(12,2) NOT NULL,
    time_deal_stock_id uuid NOT NULL,
    unit_price numeric(12,2),
    order_id uuid NOT NULL
);
CREATE TABLE order_schema.p_order_reservation (
    order_reservation_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    confirmed_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone NOT NULL,
    quantity bigint NOT NULL,
    released_at timestamp(6) with time zone,
    reserved_at timestamp(6) with time zone NOT NULL,
    status character varying(20) NOT NULL,
    time_deal_stock_id uuid NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT p_order_reservation_status_check CHECK (((status)::text = ANY ((ARRAY['RESERVED'::character varying, 'CONFIRMED'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE order_schema.p_outbox_event (
    event_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_type character varying(50) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    error_message text,
    event_type character varying(100) NOT NULL,
    failed_at timestamp(6) with time zone,
    payload text NOT NULL,
    published_at timestamp(6) with time zone,
    retry_count integer NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT p_outbox_event_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);
CREATE TABLE order_schema.p_saga_instance (
    saga_id uuid NOT NULL,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    error_message text,
    failed_at timestamp(6) with time zone,
    order_id uuid,
    saga_data text,
    saga_type character varying(50) NOT NULL,
    status character varying(20) NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT p_saga_instance_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'WAITING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'COMPENSATING'::character varying, 'COMPENSATED'::character varying])::text[])))
);
CREATE TABLE order_schema.p_saga_step (
    saga_step_id uuid NOT NULL,
    compensated_at timestamp(6) with time zone,
    error_message text,
    executed_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    step_name character varying(50) NOT NULL,
    saga_id uuid NOT NULL,
    CONSTRAINT p_saga_step_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'WAITING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'COMPENSATING'::character varying, 'COMPENSATED'::character varying])::text[])))
);
ALTER TABLE ONLY order_schema.p_order_history
    ADD CONSTRAINT p_order_history_pkey PRIMARY KEY (order_history_id);
ALTER TABLE ONLY order_schema.p_order_item
    ADD CONSTRAINT p_order_item_pkey PRIMARY KEY (order_item_id);
ALTER TABLE ONLY order_schema.p_order
    ADD CONSTRAINT p_order_pkey PRIMARY KEY (order_id);
ALTER TABLE ONLY order_schema.p_order_reservation
    ADD CONSTRAINT p_order_reservation_pkey PRIMARY KEY (order_reservation_id);
ALTER TABLE ONLY order_schema.p_outbox_event
    ADD CONSTRAINT p_outbox_event_pkey PRIMARY KEY (event_id);
ALTER TABLE ONLY order_schema.p_saga_instance
    ADD CONSTRAINT p_saga_instance_pkey PRIMARY KEY (saga_id);
ALTER TABLE ONLY order_schema.p_saga_step
    ADD CONSTRAINT p_saga_step_pkey PRIMARY KEY (saga_step_id);
CREATE INDEX idx_aggregate_id ON order_schema.p_outbox_event USING btree (aggregate_id);
CREATE INDEX idx_status_created_at ON order_schema.p_outbox_event USING btree (status, created_at);
ALTER TABLE ONLY order_schema.p_order_history
    ADD CONSTRAINT fkbg7hw1tndexoesn5nyr73c8al FOREIGN KEY (order_id) REFERENCES order_schema.p_order(order_id);
ALTER TABLE ONLY order_schema.p_order_reservation
    ADD CONSTRAINT fkea0rc18vccik2nn7ic4ysq2b8 FOREIGN KEY (order_id) REFERENCES order_schema.p_order(order_id);
ALTER TABLE ONLY order_schema.p_order_item
    ADD CONSTRAINT fkjcn9cddtm7qtomdwqg8aqty92 FOREIGN KEY (order_id) REFERENCES order_schema.p_order(order_id);
ALTER TABLE ONLY order_schema.p_saga_step
    ADD CONSTRAINT fksvxlanjx9worpo2fqixsemhcm FOREIGN KEY (saga_id) REFERENCES order_schema.p_saga_instance(saga_id);
