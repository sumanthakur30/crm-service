-- Won/lost close reasons catalog + opportunity close fields.

CREATE TABLE IF NOT EXISTS crm_close_reason (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    outcome         VARCHAR(16)  NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_close_reason_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_crm_close_reason_outcome CHECK (outcome IN ('WON', 'LOST', 'BOTH'))
);

CREATE INDEX IF NOT EXISTS idx_crm_close_reason_tenant_outcome
    ON crm_close_reason (tenant_id, outcome)
    WHERE deleted_at IS NULL AND active = TRUE;

ALTER TABLE crm_opportunity
    ADD COLUMN IF NOT EXISTS close_reason_code VARCHAR(64) NULL;

ALTER TABLE crm_opportunity
    ADD COLUMN IF NOT EXISTS close_reason_note VARCHAR(512) NULL;

COMMENT ON COLUMN crm_opportunity.close_reason_code IS 'Catalog code from crm_close_reason when status is WON/LOST';
COMMENT ON COLUMN crm_opportunity.close_reason_note IS 'Optional free-text note with close reason';
