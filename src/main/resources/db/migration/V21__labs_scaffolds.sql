-- 12-month labs scaffolds: journeys, territories, marketplace connectors, CPQ options

CREATE TABLE IF NOT EXISTS crm_journey (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT ck_crm_journey_status CHECK (status IN ('DRAFT', 'ACTIVE'))
);
CREATE INDEX IF NOT EXISTS idx_crm_journey_tenant ON crm_journey (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS crm_journey_step (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    journey_id   BIGINT       NOT NULL REFERENCES crm_journey (id),
    sort_order   INT          NOT NULL DEFAULT 0,
    step_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_crm_journey_step_journey ON crm_journey_step (tenant_id, journey_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS crm_territory (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    region_code  VARCHAR(64),
    geo_json     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_crm_territory_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_crm_territory_tenant ON crm_territory (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS crm_connector (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    provider     VARCHAR(64)  NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'STUB',
    config_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT uq_crm_connector_provider UNIQUE (tenant_id, provider)
);
CREATE INDEX IF NOT EXISTS idx_crm_connector_tenant ON crm_connector (tenant_id) WHERE deleted_at IS NULL;

ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS territory_code VARCHAR(64);

ALTER TABLE crm_quotation ADD COLUMN IF NOT EXISTS cpq_options_json JSONB;
