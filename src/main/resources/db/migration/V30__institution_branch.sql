-- Campus is optional. Existing leads keep a null branch_id and keep working.

CREATE TABLE IF NOT EXISTS crm_institution (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_institution_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_crm_institution_tenant
    ON crm_institution (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS crm_branch (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    institution_id  BIGINT       NOT NULL REFERENCES crm_institution(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    city            VARCHAR(128) NULL,
    state_code      VARCHAR(8)   NULL,
    pincode         VARCHAR(16)  NULL,
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_branch_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_crm_branch_tenant
    ON crm_branch (tenant_id) WHERE deleted_at IS NULL;

ALTER TABLE crm_lead
    ADD COLUMN IF NOT EXISTS branch_id BIGINT NULL REFERENCES crm_branch(id);

ALTER TABLE crm_team_member
    ADD COLUMN IF NOT EXISTS branch_id BIGINT NULL REFERENCES crm_branch(id);

INSERT INTO crm_institution (tenant_id, code, name)
SELECT w.tenant_id, 'MAIN', w.name
FROM crm_workspace w
WHERE w.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND NOT EXISTS (
      SELECT 1 FROM crm_institution i
      WHERE i.tenant_id = w.tenant_id AND i.code = 'MAIN' AND i.deleted_at IS NULL
  );

INSERT INTO crm_branch (tenant_id, institution_id, code, name, timezone)
SELECT i.tenant_id, i.id, 'MAIN', 'Main campus', 'Asia/Kolkata'
FROM crm_institution i
WHERE i.code = 'MAIN'
  AND i.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_branch b
      WHERE b.tenant_id = i.tenant_id AND b.code = 'MAIN' AND b.deleted_at IS NULL
  );
