-- ============================================================
-- Payments (Prompt 97): one row per Stripe PaymentIntent attempt against a booking.
-- Multiple PENDING/FAILED rows can exist for the same booking (retries), but the partial
-- unique index below guarantees at most one SUCCEEDED row per booking - a booking cannot
-- be paid for twice. Payments are additive, not blocking: nothing about the bookings
-- table or its own lifecycle changes here.
-- ============================================================
CREATE TABLE payments (
    id                        BIGSERIAL      PRIMARY KEY,
    booking_id                BIGINT         NOT NULL REFERENCES bookings(id),
    amount                    NUMERIC(10, 2) NOT NULL,
    currency                  VARCHAR(3)     NOT NULL DEFAULT 'usd',
    status                    VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    stripe_payment_intent_id  VARCHAR(255)   UNIQUE,
    failure_reason            TEXT,
    paid_at                   TIMESTAMP(6),
    refunded_at               TIMESTAMP(6),

    created_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                BIGINT,
    updated_by                BIGINT
);

CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_payments_status ON payments(status);

-- At most one successful payment per booking; PENDING/FAILED retry rows are unrestricted.
CREATE UNIQUE INDEX ux_payments_booking_succeeded ON payments(booking_id) WHERE status = 'SUCCEEDED';

-- Channel flags mirror V16's BOOKING_CONFIRMED/BOOKING_COMPLETED precedent: low-urgency,
-- in-app + email + web-push, no SMS.
INSERT INTO notification_templates (template_key, type, subject, message_body, is_in_app, is_email, is_sms, is_web_push, created_at, updated_at)
VALUES
    ('PAYMENT_RECEIVED', 'BOOKING', 'Payment received',
     'Hi {{elderName}}, a payment of {{amount}} {{currency}} for your {{serviceName}} booking on {{bookingDate}} was received.',
     true, true, false, true, now(), now()),

    ('PAYMENT_FAILED', 'BOOKING', 'Payment failed',
     'Hi {{elderName}}, a payment attempt for your {{serviceName}} booking on {{bookingDate}} failed: {{reason}}. Please try again.',
     true, true, false, true, now(), now());
