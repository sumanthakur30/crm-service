-- Sprint 2: configurable duplicate rules + GSTIN index for accounts

CREATE TABLE IF NOT EXISTS crm_duplicate_rule (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL DEFAULT 'LEAD',
    match_field     VARCHAR(32)  NOT NULL,
    normalize_mode  VARCHAR(32)  NOT NULL DEFAULT 'EXACT',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    weight          INT          NOT NULL DEFAULT 10,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_duplicate_rule UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_crm_duplicate_rule_tenant
    ON crm_duplicate_rule (tenant_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_crm_account_gstin
    ON crm_account (tenant_id, gstin) WHERE deleted_at IS NULL AND gstin IS NOT NULL;

COMMENT ON TABLE crm_duplicate_rule IS 'Configurable duplicate match rules (PHONE/EMAIL/GSTIN) per tenant';
