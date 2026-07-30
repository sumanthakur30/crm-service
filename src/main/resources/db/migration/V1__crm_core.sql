-- Phase 1 CRM core: workspace, pipeline/stage, generic lead (no industry columns).

CREATE TABLE crm_workspace (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    template_code   VARCHAR(64)  NOT NULL DEFAULT 'GENERIC',
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    currency        VARCHAR(8)   NOT NULL DEFAULT 'INR',
    settings_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_workspace_tenant UNIQUE (tenant_id)
);

CREATE TABLE crm_pipeline (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    object_type     VARCHAR(32)  NOT NULL DEFAULT 'LEAD',
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_pipeline_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_crm_pipeline_object CHECK (object_type IN ('LEAD', 'OPPORTUNITY'))
);

CREATE INDEX idx_crm_pipeline_tenant ON crm_pipeline (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_stage (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    pipeline_id     BIGINT       NOT NULL REFERENCES crm_pipeline(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    probability     INT          NOT NULL DEFAULT 0,
    is_won          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_lost         BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_stage_pipeline_code UNIQUE (pipeline_id, code),
    CONSTRAINT ck_crm_stage_probability CHECK (probability >= 0 AND probability <= 100)
);

CREATE INDEX idx_crm_stage_tenant ON crm_stage (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_lead (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    pipeline_id     BIGINT       NOT NULL REFERENCES crm_pipeline(id),
    stage_id        BIGINT       NOT NULL REFERENCES crm_stage(id),
    title           VARCHAR(256) NOT NULL,
    display_name    VARCHAR(256) NULL,
    company_name    VARCHAR(256) NULL,
    email           VARCHAR(256) NULL,
    phone           VARCHAR(64)  NULL,
    source_code     VARCHAR(64)  NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(32)  NOT NULL DEFAULT 'MEDIUM',
    score           INT          NOT NULL DEFAULT 0,
    owner_user_id   VARCHAR(64)  NULL,
    team_id         VARCHAR(64)  NULL,
    amount          NUMERIC(18, 2) NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'INR',
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    external_refs   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    form_key        VARCHAR(128) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_lead_status CHECK (status IN ('OPEN', 'QUALIFIED', 'CONVERTED', 'LOST', 'DUPLICATE')),
    CONSTRAINT ck_crm_lead_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'HOT')),
    CONSTRAINT ck_crm_lead_score CHECK (score >= 0 AND score <= 100)
);

CREATE INDEX idx_crm_lead_tenant ON crm_lead (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_lead_tenant_stage ON crm_lead (tenant_id, stage_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_lead_tenant_owner ON crm_lead (tenant_id, owner_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_lead_tenant_phone ON crm_lead (tenant_id, phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE INDEX idx_crm_lead_tenant_email ON crm_lead (tenant_id, email) WHERE deleted_at IS NULL AND email IS NOT NULL;
