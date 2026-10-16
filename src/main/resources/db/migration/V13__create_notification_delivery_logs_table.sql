CREATE TABLE notification_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_notification_delivery_logs_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_delivery_logs_notification_id ON notification_delivery_logs (notification_id);
