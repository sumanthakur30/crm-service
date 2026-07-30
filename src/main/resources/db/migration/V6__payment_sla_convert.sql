-- Phase 2c: payment links, SLA tasks, convert audit.

ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS payment_link_url VARCHAR(512) NULL;
ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS payment_status VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(32) NULL;
ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS payment_ref VARCHAR(128) NULL;
ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS payment_amount NUMERIC(18, 2) NULL;
ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ NULL;

ALTER TABLE crm_quotation DROP CONSTRAINT IF EXISTS ck_crm_quote_payment_status;
ALTER TABLE crm_quotation
    ADD CONSTRAINT ck_crm_quote_payment_status CHECK (
        payment_status IN ('NONE', 'LINK_CREATED', 'PENDING', 'PAID', 'FAILED', 'CANCELLED')
    );

CREATE TABLE crm_sla_policy (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    object_type     VARCHAR(32)  NOT NULL DEFAULT 'LEAD',
    idle_hours      INT          NOT NULL DEFAULT 24,
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    task_title      VARCHAR(256) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_sla_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_crm_sla_object CHECK (object_type IN ('LEAD', 'OPPORTUNITY')),
    CONSTRAINT ck_crm_sla_idle CHECK (idle_hours > 0)
);

CREATE INDEX idx_crm_sla_tenant ON crm_sla_policy (tenant_id) WHERE deleted_at IS NULL AND active = TRUE;

CREATE TABLE crm_task (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    related_type    VARCHAR(32)  NOT NULL,
    related_id      BIGINT       NOT NULL,
    title           VARCHAR(256) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    due_at          TIMESTAMPTZ  NULL,
    owner_user_id   VARCHAR(64)  NULL,
    source_code     VARCHAR(64)  NULL,
    sla_policy_id   BIGINT       NULL REFERENCES crm_sla_policy (id),
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ  NULL,
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_task_related CHECK (related_type IN ('LEAD', 'OPPORTUNITY', 'QUOTATION')),
    CONSTRAINT ck_crm_task_status CHECK (status IN ('OPEN', 'DONE', 'CANCELLED'))
);

CREATE INDEX idx_crm_task_tenant_status ON crm_task (tenant_id, status, due_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_task_related ON crm_task (tenant_id, related_type, related_id)
    WHERE deleted_at IS NULL;

CREATE TABLE crm_convert_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NOT NULL REFERENCES crm_lead (id),
    target_system   VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    request_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    response_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    external_id     VARCHAR(128) NULL,
    error_message   VARCHAR(512) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_convert_status CHECK (status IN ('QUEUED', 'SENT', 'ACKED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_crm_convert_lead ON crm_convert_event (tenant_id, lead_id);
