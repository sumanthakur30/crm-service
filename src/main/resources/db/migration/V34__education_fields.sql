CREATE TABLE IF NOT EXISTS crm_field_def (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    label           VARCHAR(128) NOT NULL,
    data_type       VARCHAR(32)  NOT NULL,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order      INT          NOT NULL DEFAULT 0,
    options         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_field_def UNIQUE (tenant_id, object_type, code),
    CONSTRAINT ck_crm_field_def_object CHECK (object_type IN ('LEAD', 'CONTACT')),
    CONSTRAINT ck_crm_field_def_type CHECK (data_type IN ('TEXT', 'NUMBER', 'DATE', 'SINGLE_SELECT', 'MULTI_SELECT'))
);

CREATE TABLE IF NOT EXISTS crm_field_value (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    object_id       BIGINT       NOT NULL,
    field_def_id    BIGINT       NOT NULL REFERENCES crm_field_def(id),
    value_text      VARCHAR(512) NULL,
    value_number    NUMERIC(18, 4) NULL,
    value_date      DATE         NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_field_value UNIQUE (object_type, object_id, field_def_id),
    CONSTRAINT ck_crm_field_value_object CHECK (object_type IN ('LEAD', 'CONTACT'))
);

CREATE INDEX IF NOT EXISTS idx_crm_field_value_lookup
    ON crm_field_value (tenant_id, object_type, field_def_id, value_text)
    WHERE deleted_at IS NULL;

INSERT INTO crm_field_def (tenant_id, object_type, code, label, data_type, sort_order, options)
SELECT w.tenant_id, 'LEAD', f.code, f.label, f.data_type, f.sort_order, f.options::jsonb
FROM crm_workspace w
CROSS JOIN (
    VALUES
        ('course', 'Course', 'TEXT', 10, '[]'),
        ('program', 'Program', 'TEXT', 20, '[]'),
        ('class_grade', 'Class', 'TEXT', 30, '[]'),
        ('academic_session', 'Session', 'TEXT', 40, '[]'),
        ('admission_year', 'Admission year', 'TEXT', 50, '[]'),
        ('study_preference', 'Study preference', 'SINGLE_SELECT', 60, '["Day","Hostel","Online"]')
) AS f(code, label, data_type, sort_order, options)
WHERE w.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND NOT EXISTS (
      SELECT 1 FROM crm_field_def d
      WHERE d.tenant_id = w.tenant_id AND d.object_type = 'LEAD' AND d.code = f.code AND d.deleted_at IS NULL
  );

INSERT INTO crm_close_reason (tenant_id, code, name, outcome, sort_order)
SELECT w.tenant_id, r.code, r.name, 'LOST', r.sort_order
FROM crm_workspace w
CROSS JOIN (
    VALUES
        ('FEE', 'Fees', 10),
        ('DISTANCE', 'Distance', 20),
        ('NOT_INTERESTED', 'Not interested', 30),
        ('JOINED_ELSEWHERE', 'Joined another school', 40),
        ('NO_RESPONSE', 'No response', 50)
) AS r(code, name, sort_order)
WHERE w.deleted_at IS NULL
  AND w.template_code = 'EDUCATION'
  AND NOT EXISTS (
      SELECT 1 FROM crm_close_reason c
      WHERE c.tenant_id = w.tenant_id AND c.code = r.code AND c.deleted_at IS NULL
  );
