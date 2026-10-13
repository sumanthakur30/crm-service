-- A call can belong to a deal as well as a lead. Same table, no second call log.
ALTER TABLE crm_call_log
    ADD COLUMN opportunity_id BIGINT NULL REFERENCES crm_opportunity (id);

CREATE INDEX idx_crm_call_opportunity
    ON crm_call_log (tenant_id, opportunity_id, occurred_at DESC);
