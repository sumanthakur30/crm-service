-- Sprint 8: Hot/Warm/Cold score bands + qualification schemas

CREATE TABLE IF NOT EXISTS crm_score_band (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    hot_min         INT          NOT NULL DEFAULT 70,
    warm_min        INT          NOT NULL DEFAULT 40,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_score_band_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_crm_score_band_range CHECK (
        warm_min >= 0 AND hot_min >= warm_min AND hot_min <= 100
    )
);

COMMENT ON TABLE crm_score_band IS 'Tenant Hot/Warm/Cold thresholds: HOT >= hot_min, WARM >= warm_min, else COLD';

CREATE TABLE IF NOT EXISTS crm_qualification_schema (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    fields_json     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_qualification_schema UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_crm_qualification_schema_tenant
    ON crm_qualification_schema (tenant_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE crm_qualification_schema IS 'Configurable qualification checklists (e.g. BANT); answers live on lead.attributes.qualification';
