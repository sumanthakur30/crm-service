-- Quote versioning lineage + discount approval status.

ALTER TABLE crm_quotation
    ADD COLUMN IF NOT EXISTS parent_quotation_id BIGINT NULL;

ALTER TABLE crm_quotation
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE crm_quotation
    ADD COLUMN IF NOT EXISTS approval_id BIGINT NULL;

COMMENT ON COLUMN crm_quotation.parent_quotation_id IS 'Prior version id when this row was created via revise';
COMMENT ON COLUMN crm_quotation.approval_status IS 'NONE | PENDING | APPROVED | REJECTED — discount gate before send';
COMMENT ON COLUMN crm_quotation.approval_id IS 'crm_approval.id for discount approval when pending/decided';

CREATE INDEX IF NOT EXISTS idx_crm_quotation_quote_number
    ON crm_quotation (tenant_id, quote_number)
    WHERE deleted_at IS NULL;
