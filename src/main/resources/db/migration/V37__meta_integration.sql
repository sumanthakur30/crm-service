-- Meta Lead Ads: encrypted tokens, selected pages and forms.
-- Also allow the website capture provider that phase 2 already writes.

ALTER TABLE crm_inbound_event DROP CONSTRAINT ck_crm_inbound_provider;
ALTER TABLE crm_inbound_event ADD CONSTRAINT ck_crm_inbound_provider CHECK (
    provider IN ('META', 'GOOGLE', 'MISSED_CALL', 'CHATBOT', 'WEB')
);

CREATE TABLE crm_integration (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            VARCHAR(64)  NOT NULL,
    provider             VARCHAR(32)  NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'DISCONNECTED',
    display_name         VARCHAR(160) NULL,
    connected_at         TIMESTAMPTZ  NULL,
    last_success_at      TIMESTAMPTZ  NULL,
    last_error           VARCHAR(500) NULL,
    last_token_check_at  TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_integration_provider CHECK (
        provider IN ('META', 'GOOGLE_ADS', 'WHATSAPP', 'TELEPHONY', 'WEBSITE')
    ),
    CONSTRAINT ck_crm_integration_status CHECK (
        status IN ('DISCONNECTED', 'CONNECTED', 'DEGRADED', 'ERROR')
    ),
    CONSTRAINT uq_crm_integration_provider UNIQUE (tenant_id, provider)
);

CREATE TABLE crm_integration_asset (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    integration_id      BIGINT       NOT NULL REFERENCES crm_integration(id) ON DELETE CASCADE,
    asset_type          VARCHAR(32)  NOT NULL,
    external_id         VARCHAR(128) NOT NULL,
    name                VARCHAR(256) NULL,
    selected            BOOLEAN      NOT NULL DEFAULT FALSE,
    branch_id           BIGINT       NULL REFERENCES crm_branch(id) ON DELETE SET NULL,
    campaign_id         BIGINT       NULL REFERENCES crm_campaign(id) ON DELETE SET NULL,
    parent_external_id  VARCHAR(128) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_integration_asset_type CHECK (
        asset_type IN ('PAGE', 'IG_ACCOUNT', 'AD_ACCOUNT', 'FORM', 'CUSTOMER', 'PHONE_NUMBER', 'BRANCH_LINK')
    ),
    CONSTRAINT uq_crm_integration_asset UNIQUE (integration_id, asset_type, external_id)
);

CREATE INDEX idx_crm_integration_asset_ext
    ON crm_integration_asset (asset_type, external_id);

CREATE TABLE crm_integration_secret (
    id              BIGSERIAL PRIMARY KEY,
    integration_id  BIGINT       NOT NULL REFERENCES crm_integration(id) ON DELETE CASCADE,
    asset_id        BIGINT       NULL REFERENCES crm_integration_asset(id) ON DELETE CASCADE,
    secret_kind     VARCHAR(32)  NOT NULL,
    ciphertext      TEXT         NOT NULL,
    expires_at      TIMESTAMPTZ  NULL,
    rotated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_integration_secret_kind CHECK (
        secret_kind IN ('USER_TOKEN', 'PAGE_TOKEN', 'WEBHOOK_KEY', 'REFRESH_TOKEN')
    )
);

CREATE INDEX idx_crm_integration_secret_asset ON crm_integration_secret (asset_id);

ALTER TABLE crm_field_map ALTER COLUMN form_id DROP NOT NULL;
ALTER TABLE crm_field_map ADD COLUMN asset_id BIGINT NULL REFERENCES crm_integration_asset(id) ON DELETE CASCADE;
ALTER TABLE crm_field_map ADD CONSTRAINT ck_crm_field_map_owner CHECK (form_id IS NOT NULL OR asset_id IS NOT NULL);
CREATE UNIQUE INDEX uq_crm_field_map_asset_key
    ON crm_field_map (asset_id, external_key)
    WHERE asset_id IS NOT NULL;
