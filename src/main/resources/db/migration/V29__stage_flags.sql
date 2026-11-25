-- Stage flags used by education counts. Old stages stay false until a template or an admin sets them.

ALTER TABLE crm_stage
    ADD COLUMN IF NOT EXISTS is_qualified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_contacted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_application BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE crm_lead
    ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN crm_lead.first_response_at IS 'Set once, from the first connected call. Later calls do not move it.';

UPDATE crm_stage s
SET is_contacted = TRUE
FROM crm_pipeline p
JOIN crm_workspace w ON w.tenant_id = p.tenant_id AND w.deleted_at IS NULL
WHERE s.pipeline_id = p.id
  AND s.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND s.code IN ('COUNSELING', 'APPLICATION', 'FEE_QUOTE', 'ENROLLED');

UPDATE crm_stage s
SET is_application = TRUE
FROM crm_pipeline p
JOIN crm_workspace w ON w.tenant_id = p.tenant_id AND w.deleted_at IS NULL
WHERE s.pipeline_id = p.id
  AND s.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND s.code IN ('APPLICATION', 'FEE_QUOTE', 'ENROLLED');

UPDATE crm_stage s
SET is_qualified = TRUE
FROM crm_pipeline p
JOIN crm_workspace w ON w.tenant_id = p.tenant_id AND w.deleted_at IS NULL
WHERE s.pipeline_id = p.id
  AND s.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND s.code IN ('FEE_QUOTE', 'ENROLLED');
