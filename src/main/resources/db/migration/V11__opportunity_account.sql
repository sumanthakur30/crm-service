ALTER TABLE crm_opportunity
    ADD COLUMN IF NOT EXISTS account_id BIGINT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_crm_opportunity_account'
    ) THEN
        ALTER TABLE crm_opportunity
            ADD CONSTRAINT fk_crm_opportunity_account
            FOREIGN KEY (account_id) REFERENCES crm_account (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_crm_opportunity_tenant_account
    ON crm_opportunity (tenant_id, account_id)
    WHERE deleted_at IS NULL AND account_id IS NOT NULL;
