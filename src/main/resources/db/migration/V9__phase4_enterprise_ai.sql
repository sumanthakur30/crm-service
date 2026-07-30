-- Phase 4: Enterprise AI insights + residency + audit export + SSO metadata.

CREATE TABLE crm_ai_insight (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    related_type    VARCHAR(32)  NOT NULL,
    related_id      BIGINT       NOT NULL,
    insight_type    VARCHAR(32)  NOT NULL,
    title           VARCHAR(256) NOT NULL,
    body            TEXT         NOT NULL,
    confidence      NUMERIC(5, 2) NOT NULL DEFAULT 0,
    model_code      VARCHAR(64)  NOT NULL DEFAULT 'HEURISTIC_V1',
    language_code   VARCHAR(16)  NOT NULL DEFAULT 'en',
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_ai_related CHECK (related_type IN ('LEAD', 'OPPORTUNITY', 'QUOTATION', 'TENANT')),
    CONSTRAINT ck_crm_ai_type CHECK (
        insight_type IN (
            'SUMMARY', 'NBA', 'WIN_PREDICTION', 'CHURN_RISK', 'UPSELL',
            'OCR_CARD', 'DRAFT', 'COPILOT', 'SCORE_EXPLAIN'
        )
    )
);

CREATE INDEX idx_crm_ai_insight_related ON crm_ai_insight (tenant_id, related_type, related_id, created_at DESC);
CREATE INDEX idx_crm_ai_insight_type ON crm_ai_insight (tenant_id, insight_type, created_at DESC);

CREATE TABLE crm_tenant_enterprise (
    tenant_id           VARCHAR(64) PRIMARY KEY,
    data_residency      VARCHAR(32)  NOT NULL DEFAULT 'IN',
    sso_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    sso_provider        VARCHAR(64)  NULL,
    sso_metadata_url    VARCHAR(512) NULL,
    audit_export_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    preferred_language  VARCHAR(16)  NOT NULL DEFAULT 'en',
    attributes          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_residency CHECK (data_residency IN ('IN', 'EU', 'US', 'APAC', 'GLOBAL'))
);

CREATE TABLE crm_audit_export_job (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    format          VARCHAR(16)  NOT NULL DEFAULT 'JSON',
    from_at         TIMESTAMPTZ  NULL,
    to_at           TIMESTAMPTZ  NULL,
    requested_by    VARCHAR(64)  NULL,
    artifact_url    VARCHAR(512) NULL,
    row_count       INT          NOT NULL DEFAULT 0,
    error_message   VARCHAR(512) NULL,
    result_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_audit_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT ck_crm_audit_format CHECK (format IN ('JSON', 'CSV'))
);

CREATE INDEX idx_crm_audit_export_tenant ON crm_audit_export_job (tenant_id, created_at DESC);
