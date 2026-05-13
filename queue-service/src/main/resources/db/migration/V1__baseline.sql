CREATE TABLE queue_schema.p_queue_policy (
    policy_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by bigint,
    deleted_at timestamp(6) without time zone,
    deleted_by bigint,
    updated_at timestamp(6) without time zone NOT NULL,
    updated_by bigint,
    product_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    end_time timestamp(6) without time zone NOT NULL,
    start_time timestamp(6) without time zone NOT NULL,
    limit_size integer NOT NULL,
    max_capacity integer NOT NULL,
    queue_gap integer NOT NULL,
    ttl integer NOT NULL,
    CONSTRAINT p_queue_policy_status_check CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'PAUSED'::character varying, 'STOPPED'::character varying])::text[])))
);
ALTER TABLE ONLY queue_schema.p_queue_policy
    ADD CONSTRAINT p_queue_policy_pkey PRIMARY KEY (policy_id);
