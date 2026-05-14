-- ORDER_PURCHASE_CONFIRMED 알림 타입 추가
ALTER TABLE notification_schema.p_notification
    DROP CONSTRAINT IF EXISTS p_notification_type_check;

ALTER TABLE notification_schema.p_notification
    ADD CONSTRAINT p_notification_type_check
    CHECK (type::text = ANY (ARRAY[
        'ORDER_CREATED',
        'ORDER_PAID',
        'ORDER_CANCELED',
        'ORDER_PURCHASE_CONFIRMED',
        'PAYMENT_FAILED',
        'TIMEDEAL_STARTED',
        'TIMEDEAL_ENDING_SOON',
        'TIMEDEAL_SOLD_OUT',
        'TIMEDEAL_ENDED',
        'SELLER_TIMEDEAL_STARTED',
        'SELLER_TIMEDEAL_ENDED',
        'SELLER_TIMEDEAL_SOLD_OUT',
        'ACCOUNT_BLOCKED',
        'ACCOUNT_UNBLOCKED',
        'ROLE_CHANGED'
    ]::text[]));
