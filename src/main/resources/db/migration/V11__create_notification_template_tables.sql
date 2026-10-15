CREATE TABLE notification_templates (
    id BIGSERIAL PRIMARY KEY,
    template_key VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    message_body TEXT,
    is_in_app BOOLEAN NOT NULL DEFAULT FALSE,
    is_email BOOLEAN NOT NULL DEFAULT FALSE,
    is_sms BOOLEAN NOT NULL DEFAULT FALSE,
    is_web_push BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT uk_notification_templates_template_key UNIQUE (template_key)
);

CREATE TABLE notification_template_variables (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    variable_name VARCHAR(255) NOT NULL,
    html_content TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,

    CONSTRAINT fk_notification_template_variables_template FOREIGN KEY (template_id)
        REFERENCES notification_templates (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_template_variables_template_id ON notification_template_variables (template_id);
