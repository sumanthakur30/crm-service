-- A touch is insert-only. A later enquiry adds a row; it does not rewrite an older campaign.

CREATE TABLE IF NOT EXISTS crm_touch (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NOT NULL REFERENCES crm_lead(id),
    contact_id      BIGINT       NULL REFERENCES crm_contact(id),
    source_code     VARCHAR(64)  NOT NULL,
    source_category VARCHAR(32)  NOT NULL,
    provider        VARCHAR(32)  NOT NULL,
    external_id     VARCHAR(128) NULL,
    campaign_id     BIGINT       NULL REFERENCES crm_campaign(id),
    branch_id       BIGINT       NULL REFERENCES crm_branch(id),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL,
    raw_event_id    BIGINT       NULL REFERENCES crm_inbound_event(id),
    field_values    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_crm_touch_category CHECK (source_category IN ('PAID', 'ORGANIC', 'OFFLINE', 'PARTNER')),
    CONSTRAINT ck_crm_touch_provider CHECK (provider IN ('META', 'GOOGLE', 'WEB', 'WHATSAPP', 'TELEPHONY', 'FILE', 'USER'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_touch_provider_ext
    ON crm_touch (tenant_id, provider, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_touch_lead
    ON crm_touch (tenant_id, lead_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_touch_campaign
    ON crm_touch (tenant_id, campaign_id, occurred_at)
    WHERE campaign_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS crm_attribution (
    touch_id              BIGINT PRIMARY KEY REFERENCES crm_touch(id),
    campaign_external_id  VARCHAR(128) NULL,
    campaign_name         VARCHAR(256) NULL,
    adset_external_id     VARCHAR(128) NULL,
    adset_name            VARCHAR(256) NULL,
    ad_external_id        VARCHAR(128) NULL,
    ad_name               VARCHAR(256) NULL,
    form_external_id      VARCHAR(128) NULL,
    form_name             VARCHAR(256) NULL,
    keyword               VARCHAR(256) NULL,
    landing_url           VARCHAR(512) NULL,
    utm_source            VARCHAR(128) NULL,
    utm_medium            VARCHAR(128) NULL,
    utm_campaign          VARCHAR(128) NULL,
    utm_content           VARCHAR(128) NULL,
    utm_term              VARCHAR(128) NULL,
    gclid                 VARCHAR(256) NULL,
    gbraid                VARCHAR(256) NULL,
    wbraid                VARCHAR(256) NULL,
    ctwa_clid             VARCHAR(256) NULL,
    page_external_id      VARCHAR(128) NULL,
    instagram_actor_id    VARCHAR(128) NULL
);

ALTER TABLE crm_inbound_event
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS signature_ok BOOLEAN NULL,
    ADD COLUMN IF NOT EXISTS branch_id BIGINT NULL REFERENCES crm_branch(id);
