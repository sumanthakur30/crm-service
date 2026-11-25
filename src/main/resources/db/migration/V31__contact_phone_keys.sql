-- Phone is the lookup key. Email is indexed but not unique: siblings often share a parent email.
-- A unique mobile index is deferred until a cleanup job, because old rows may repeat a number.

ALTER TABLE crm_contact
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS phone_e164 VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS phone_raw VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS alternate_phone_e164 VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS email_normalized VARCHAR(256) NULL,
    ADD COLUMN IF NOT EXISTS city VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS state_code VARCHAR(8) NULL,
    ADD COLUMN IF NOT EXISTS contact_kind VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS do_not_contact BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE crm_contact DROP CONSTRAINT IF EXISTS ck_crm_contact_kind;
ALTER TABLE crm_contact
    ADD CONSTRAINT ck_crm_contact_kind CHECK (contact_kind IN ('STUDENT', 'PARENT', 'OTHER'));

CREATE INDEX IF NOT EXISTS idx_crm_contact_phone_e164
    ON crm_contact (tenant_id, phone_e164)
    WHERE deleted_at IS NULL AND phone_e164 IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_crm_contact_email_normalized
    ON crm_contact (tenant_id, email_normalized)
    WHERE deleted_at IS NULL AND email_normalized IS NOT NULL;
