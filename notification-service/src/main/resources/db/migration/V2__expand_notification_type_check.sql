-- NotificationType enum에 추가된 값들을 체크 제약에 반영
-- 추가: TIMEDEAL_ENDING_SOON, TIMEDEAL_SOLD_OUT,
--      SELLER_TIMEDEAL_STARTED, SELLER_TIMEDEAL_ENDED, SELLER_TIMEDEAL_SOLD_OUT
ALTER TABLE notification_schema.p_notification
    DROP CONSTRAINT IF EXISTS p_notification_type_check;

ALTER TABLE notification_schema.p_notification
    ADD CONSTRAINT p_notification_type_check
    CHECK (type::text = ANY (ARRAY[
        'ORDER_CREATED',
        'ORDER_PAID',
        'ORDER_CANCELED',
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
