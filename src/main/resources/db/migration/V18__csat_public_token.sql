-- Public CSAT survey link (tokenized), mirrors campaign public capture pattern.

ALTER TABLE crm_case
    ADD COLUMN IF NOT EXISTS csat_public_token VARCHAR(36);

UPDATE crm_case
SET csat_public_token = gen_random_uuid()::text
WHERE csat_public_token IS NULL
  AND deleted_at IS NULL;

UPDATE crm_case
SET csat_public_token = gen_random_uuid()::text
WHERE csat_public_token IS NULL;

ALTER TABLE crm_case
    ALTER COLUMN csat_public_token SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_case_csat_public_token
    ON crm_case (csat_public_token);
