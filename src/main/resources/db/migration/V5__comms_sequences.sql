-- Phase 2b: WhatsApp/Email/SMS sequences (enrollment + steps). Dispatch via notification-service.

CREATE TABLE crm_sequence (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    channel_default VARCHAR(16)  NOT NULL DEFAULT 'WHATSAPP',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_sequence_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_crm_sequence_channel CHECK (
        channel_default IN ('WHATSAPP', 'EMAIL', 'SMS')
    )
);

CREATE INDEX idx_crm_sequence_tenant ON crm_sequence (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_sequence_step (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    sequence_id     BIGINT       NOT NULL REFERENCES crm_sequence (id),
    sort_order      INT          NOT NULL DEFAULT 10,
    delay_hours     INT          NOT NULL DEFAULT 0,
    channel         VARCHAR(16)  NOT NULL,
    subject_template VARCHAR(256) NULL,
    body_template   TEXT         NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_sequence_step_channel CHECK (
        channel IN ('WHATSAPP', 'EMAIL', 'SMS')
    ),
    CONSTRAINT ck_crm_sequence_step_delay CHECK (delay_hours >= 0)
);

CREATE INDEX idx_crm_sequence_step_seq ON crm_sequence_step (tenant_id, sequence_id)
    WHERE deleted_at IS NULL;

CREATE TABLE crm_sequence_enrollment (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    sequence_id     BIGINT       NOT NULL REFERENCES crm_sequence (id),
    lead_id         BIGINT       NULL,
    opportunity_id  BIGINT       NULL,
    recipient       VARCHAR(256) NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    current_step    INT          NOT NULL DEFAULT 0,
    next_run_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error      VARCHAR(512) NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ  NULL,
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_enrollment_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED')
    ),
    CONSTRAINT ck_crm_enrollment_channel CHECK (
        channel IN ('WHATSAPP', 'EMAIL', 'SMS')
    ),
    CONSTRAINT ck_crm_enrollment_target CHECK (
        lead_id IS NOT NULL OR opportunity_id IS NOT NULL
    )
);

CREATE INDEX idx_crm_enrollment_due ON crm_sequence_enrollment (tenant_id, status, next_run_at)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';
