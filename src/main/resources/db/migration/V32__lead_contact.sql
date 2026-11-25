CREATE TABLE IF NOT EXISTS crm_lead_contact (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NOT NULL REFERENCES crm_lead(id),
    contact_id      BIGINT       NOT NULL REFERENCES crm_contact(id),
    role            VARCHAR(32)  NOT NULL,
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_lead_contact_role UNIQUE (lead_id, contact_id, role),
    CONSTRAINT ck_crm_lead_contact_role CHECK (role IN ('STUDENT', 'FATHER', 'MOTHER', 'GUARDIAN', 'SELF'))
);

CREATE INDEX IF NOT EXISTS idx_crm_lead_contact_tenant
    ON crm_lead_contact (tenant_id, lead_id) WHERE deleted_at IS NULL;
