-- One row per capture step. Website forms finish the row in the request.
-- Pending rows are for a later fetch (Meta). None are inserted as pending in this phase.

CREATE TABLE crm_job (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    job_type    VARCHAR(64)  NOT NULL,
    ref_id      BIGINT       NULL,
    status      VARCHAR(16)  NOT NULL,
    run_at      TIMESTAMPTZ  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    last_error  VARCHAR(500) NULL,
    locked_at   TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_job_status CHECK (status IN ('PENDING', 'DONE', 'FAILED'))
);

CREATE INDEX idx_crm_job_due ON crm_job (status, run_at) WHERE status = 'PENDING';
