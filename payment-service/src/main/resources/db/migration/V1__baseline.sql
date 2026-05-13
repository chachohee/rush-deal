CREATE TABLE payment_schema.p_payment (
    payment_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    amount numeric(38,2) NOT NULL,
    order_id uuid NOT NULL,
    portone_payment_id character varying(255),
    status character varying(255) NOT NULL,
    CONSTRAINT p_payment_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE payment_schema.p_payment_transaction (
    payment_transaction_id uuid NOT NULL,
    discount bigint,
    paid bigint,
    supply bigint,
    tax_free bigint,
    total bigint,
    vat bigint,
    cancel_amount numeric(38,2),
    cancel_reason character varying(255),
    cancelled_at timestamp(6) with time zone,
    bin character varying(255),
    brand character varying(255),
    issuer character varying(255),
    name character varying(255),
    number character varying(255),
    owner_type character varying(255),
    publisher character varying(255),
    type character varying(255),
    currency character varying(255),
    requested_at timestamp(6) with time zone,
    status_changed_at timestamp(6) with time zone,
    store_id character varying(255),
    transaction_id character varying(255),
    updated_at timestamp(6) with time zone,
    payment_id uuid NOT NULL
);
ALTER TABLE ONLY payment_schema.p_payment
    ADD CONSTRAINT p_payment_pkey PRIMARY KEY (payment_id);
ALTER TABLE ONLY payment_schema.p_payment_transaction
    ADD CONSTRAINT p_payment_transaction_pkey PRIMARY KEY (payment_transaction_id);
ALTER TABLE ONLY payment_schema.p_payment_transaction
    ADD CONSTRAINT fk9ijgawtu2rfywmw5ox81o3dw0 FOREIGN KEY (payment_id) REFERENCES payment_schema.p_payment(payment_id);
