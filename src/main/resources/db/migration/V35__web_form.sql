-- Website form: public key, branch, default course, campaign, and a field map.
-- The existing campaign capture URL keeps working when the key is not a form.

CREATE TABLE crm_web_form (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    public_key      VARCHAR(64)  NOT NULL,
    name            VARCHAR(160) NOT NULL,
    branch_id       BIGINT       NULL REFERENCES crm_branch(id) ON DELETE SET NULL,
    campaign_id     BIGINT       NULL REFERENCES crm_campaign(id) ON DELETE SET NULL,
    default_course  VARCHAR(160) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'TEST',
    honeypot_field  VARCHAR(64)  NOT NULL DEFAULT 'company_website',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_web_form_status CHECK (status IN ('TEST', 'ACTIVE')),
    CONSTRAINT uq_crm_web_form_public_key UNIQUE (public_key)
);

CREATE INDEX idx_crm_web_form_tenant ON crm_web_form (tenant_id, created_at DESC);

CREATE TABLE crm_field_map (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL,
    form_id       BIGINT       NOT NULL REFERENCES crm_web_form(id) ON DELETE CASCADE,
    external_key  VARCHAR(128) NOT NULL,
    target        VARCHAR(64)  NOT NULL,
    required      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_crm_field_map_target CHECK (
        target IN ('CONTACT_PHONE', 'CONTACT_EMAIL', 'CONTACT_NAME', 'LEAD_TITLE', 'COURSE', 'STORE_ONLY')
    ),
    CONSTRAINT uq_crm_field_map_key UNIQUE (form_id, external_key)
);

CREATE INDEX idx_crm_field_map_form ON crm_field_map (form_id);
