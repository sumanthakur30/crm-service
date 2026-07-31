-- Collaborative forecast commits (overlay on pipeline-weighted forecast).

CREATE TABLE IF NOT EXISTS crm_forecast_commit (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)    NOT NULL,
    user_id      VARCHAR(128)   NOT NULL,
    period_ym    VARCHAR(7)     NOT NULL,
    amount       NUMERIC(18, 2) NOT NULL,
    currency     VARCHAR(8)     NOT NULL DEFAULT 'INR',
    note         VARCHAR(512)   NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crm_forecast_commit UNIQUE (tenant_id, user_id, period_ym),
    CONSTRAINT ck_crm_forecast_period_ym CHECK (period_ym ~ '^[0-9]{4}-[0-9]{2}$')
);

CREATE INDEX IF NOT EXISTS idx_crm_forecast_commit_tenant_period
    ON crm_forecast_commit (tenant_id, period_ym);
