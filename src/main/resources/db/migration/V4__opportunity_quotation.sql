-- Phase 2: Opportunity + GST Quotation (business-agnostic).

CREATE TABLE crm_opportunity (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    pipeline_id         BIGINT       NOT NULL REFERENCES crm_pipeline(id),
    stage_id            BIGINT       NOT NULL REFERENCES crm_stage(id),
    lead_id             BIGINT       NULL REFERENCES crm_lead(id),
    name                VARCHAR(256) NOT NULL,
    amount              NUMERIC(18, 2) NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    probability         INT          NOT NULL DEFAULT 0,
    expected_close_date DATE         NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    owner_user_id       VARCHAR(64)  NULL,
    team_id             VARCHAR(64)  NULL,
    attributes          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    external_refs       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_opp_status CHECK (status IN ('OPEN', 'WON', 'LOST', 'ABANDONED')),
    CONSTRAINT ck_crm_opp_probability CHECK (probability >= 0 AND probability <= 100)
);

CREATE INDEX idx_crm_opp_tenant ON crm_opportunity (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_opp_tenant_stage ON crm_opportunity (tenant_id, stage_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_opp_lead ON crm_opportunity (tenant_id, lead_id) WHERE deleted_at IS NULL AND lead_id IS NOT NULL;

CREATE TABLE crm_quotation (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL,
    opportunity_id      BIGINT       NOT NULL REFERENCES crm_opportunity(id),
    quote_number        VARCHAR(64)  NOT NULL,
    version_no          INT          NOT NULL DEFAULT 1,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    customer_name       VARCHAR(256) NULL,
    customer_gstin      VARCHAR(20)  NULL,
    place_of_supply     VARCHAR(64)  NULL,
    seller_state_code   VARCHAR(8)   NULL,
    buyer_state_code    VARCHAR(8)   NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    taxable_amount      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    cgst_amount         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    sgst_amount         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    igst_amount         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(18, 2) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(18, 2) NOT NULL DEFAULT 0,
    terms               TEXT         NULL,
    lines_json          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    share_payload_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    valid_until         DATE         NULL,
    accepted_at         TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_quote_tenant_number UNIQUE (tenant_id, quote_number, version_no),
    CONSTRAINT ck_crm_quote_status CHECK (
        status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')
    )
);

CREATE INDEX idx_crm_quote_tenant ON crm_quotation (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_crm_quote_opp ON crm_quotation (tenant_id, opportunity_id) WHERE deleted_at IS NULL;
