-- Phase 5 pilot polish: accounts/contacts, lead merge, geo/workload assignment fields.

ALTER TABLE crm_team_member
    ADD COLUMN IF NOT EXISTS state_codes TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS pincode_prefixes TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS open_lead_cap INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN crm_team_member.state_codes IS 'Comma-separated state codes for GEO assign (e.g. KA,MH)';
COMMENT ON COLUMN crm_team_member.pincode_prefixes IS 'Comma-separated PIN prefixes for GEO assign';
COMMENT ON COLUMN crm_team_member.open_lead_cap IS '0 = unlimited; else max open leads for WORKLOAD assign';

CREATE TABLE crm_account (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    gstin           VARCHAR(32)  NULL,
    phone           VARCHAR(64)  NULL,
    email           VARCHAR(256) NULL,
    state_code      VARCHAR(8)   NULL,
    pincode         VARCHAR(16)  NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL
);

CREATE INDEX idx_crm_account_tenant ON crm_account (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_contact (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    account_id      BIGINT       NULL REFERENCES crm_account(id),
    display_name    VARCHAR(256) NOT NULL,
    email           VARCHAR(256) NULL,
    phone           VARCHAR(64)  NULL,
    title           VARCHAR(128) NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL
);

CREATE INDEX idx_crm_contact_tenant ON crm_contact (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_contact_account ON crm_contact (tenant_id, account_id) WHERE deleted_at IS NULL;

ALTER TABLE crm_lead
    ADD COLUMN IF NOT EXISTS account_id BIGINT NULL REFERENCES crm_account(id),
    ADD COLUMN IF NOT EXISTS contact_id BIGINT NULL REFERENCES crm_contact(id),
    ADD COLUMN IF NOT EXISTS state_code VARCHAR(8) NULL,
    ADD COLUMN IF NOT EXISTS pincode VARCHAR(16) NULL,
    ADD COLUMN IF NOT EXISTS merged_into_lead_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_lead_phone ON crm_lead (tenant_id, phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_crm_lead_email ON crm_lead (tenant_id, email) WHERE deleted_at IS NULL AND email IS NOT NULL;
