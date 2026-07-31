-- CRM usage meters (seats + monthly API calls). Local counters; enforce via subscription limits when entitlements on.

CREATE TABLE IF NOT EXISTS crm_usage_counter (
    tenant_id    VARCHAR(64)  NOT NULL,
    meter_code   VARCHAR(32)  NOT NULL,
    period_key   VARCHAR(16)  NOT NULL,
    used_count   BIGINT       NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, meter_code, period_key),
    CONSTRAINT ck_crm_usage_meter CHECK (meter_code IN ('SEATS', 'API_CALLS_MONTH'))
);
