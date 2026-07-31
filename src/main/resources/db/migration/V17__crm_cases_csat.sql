-- Minimal Cases + CSAT slice (Service Hub deferred).
-- Case notes deferred; CSAT stored on case + append-only crm_csat_response.

CREATE TABLE IF NOT EXISTS crm_case (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                VARCHAR(64)  NOT NULL,
    subject                  VARCHAR(512) NOT NULL,
    status                   VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    priority                 VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    related_lead_id          BIGINT       NULL,
    related_opportunity_id   BIGINT       NULL,
    assigned_to              VARCHAR(128) NULL,
    csat_score               SMALLINT     NULL,
    csat_comment             VARCHAR(1024) NULL,
    csat_submitted_at        TIMESTAMPTZ  NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_case_status CHECK (status IN ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_crm_case_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_crm_case_csat CHECK (csat_score IS NULL OR (csat_score BETWEEN 1 AND 5))
);

CREATE INDEX IF NOT EXISTS idx_crm_case_tenant_status
    ON crm_case (tenant_id, status, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_crm_case_tenant_assigned
    ON crm_case (tenant_id, assigned_to)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS crm_csat_response (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)   NOT NULL,
    case_id         BIGINT        NOT NULL REFERENCES crm_case (id),
    score           SMALLINT      NOT NULL,
    comment         VARCHAR(1024) NULL,
    submitted_by    VARCHAR(128)  NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_csat_score CHECK (score BETWEEN 1 AND 5)
);

CREATE INDEX IF NOT EXISTS idx_crm_csat_case
    ON crm_csat_response (tenant_id, case_id, created_at DESC);
