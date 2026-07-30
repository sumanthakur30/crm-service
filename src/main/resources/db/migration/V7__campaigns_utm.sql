-- Phase 3: Campaigns + UTM attribution on leads.

CREATE TABLE crm_campaign (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    channel         VARCHAR(32)  NULL,
    utm_source      VARCHAR(128) NULL,
    utm_medium      VARCHAR(128) NULL,
    utm_campaign    VARCHAR(128) NULL,
    utm_content     VARCHAR(128) NULL,
    utm_term        VARCHAR(128) NULL,
    landing_url     VARCHAR(512) NULL,
    public_key      VARCHAR(64)  NOT NULL,
    starts_at       TIMESTAMPTZ  NULL,
    ends_at         TIMESTAMPTZ  NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_campaign_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_crm_campaign_public_key UNIQUE (public_key),
    CONSTRAINT ck_crm_campaign_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED'))
);

CREATE INDEX idx_crm_campaign_tenant ON crm_campaign (tenant_id) WHERE deleted_at IS NULL;

ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS campaign_id BIGINT NULL REFERENCES crm_campaign (id);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS utm_source VARCHAR(128) NULL;
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS utm_medium VARCHAR(128) NULL;
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS utm_campaign VARCHAR(128) NULL;
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS utm_content VARCHAR(128) NULL;
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS utm_term VARCHAR(128) NULL;

CREATE INDEX idx_crm_lead_campaign ON crm_lead (tenant_id, campaign_id)
    WHERE deleted_at IS NULL AND campaign_id IS NOT NULL;
CREATE INDEX idx_crm_lead_utm_source ON crm_lead (tenant_id, utm_source)
    WHERE deleted_at IS NULL AND utm_source IS NOT NULL;
