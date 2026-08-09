-- Sprint 10: AI usage meter

ALTER TABLE crm_usage_counter DROP CONSTRAINT IF EXISTS ck_crm_usage_meter;

ALTER TABLE crm_usage_counter
    ADD CONSTRAINT ck_crm_usage_meter
    CHECK (meter_code IN ('SEATS', 'API_CALLS_MONTH', 'AI_CALLS_MONTH'));

COMMENT ON CONSTRAINT ck_crm_usage_meter ON crm_usage_counter IS
    'Usage meters including AI_CALLS_MONTH for Sprint 10 LLM audit';
