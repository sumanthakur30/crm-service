-- Admission consultancy pack. Empty until a tenant uses the template.
-- Retail, school, and medical rows are unchanged.

ALTER TABLE crm_contact ADD COLUMN IF NOT EXISTS student_code VARCHAR(32) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_contact_student_code
    ON crm_contact (tenant_id, student_code)
    WHERE student_code IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS enquiry_no VARCHAR(32) NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_crm_lead_enquiry_no
    ON crm_lead (tenant_id, enquiry_no)
    WHERE enquiry_no IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE crm_course (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(256) NOT NULL,
    category     VARCHAR(128) NULL,
    duration_text VARCHAR(64) NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_course_code UNIQUE (tenant_id, code)
);

CREATE TABLE crm_admission_session (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_session_code UNIQUE (tenant_id, code)
);

CREATE TABLE crm_consultant (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(256) NOT NULL,
    phone       VARCHAR(64)  NULL,
    email       VARCHAR(256) NULL,
    city        VARCHAR(128) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_consultant_code UNIQUE (tenant_id, code)
);

CREATE TABLE crm_course_offer (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)    NOT NULL,
    institution_id  BIGINT         NOT NULL REFERENCES crm_institution(id),
    course_id       BIGINT         NOT NULL REFERENCES crm_course(id),
    session_id      BIGINT         NOT NULL REFERENCES crm_admission_session(id),
    fee_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ    NULL,
    CONSTRAINT uq_crm_course_offer UNIQUE (tenant_id, institution_id, course_id, session_id)
);

CREATE TABLE crm_placement (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NOT NULL REFERENCES crm_lead(id),
    contact_id      BIGINT       NULL REFERENCES crm_contact(id),
    institution_id  BIGINT       NOT NULL REFERENCES crm_institution(id),
    course_id       BIGINT       NOT NULL REFERENCES crm_course(id),
    session_id      BIGINT       NOT NULL REFERENCES crm_admission_session(id),
    consultant_id   BIGINT       NULL REFERENCES crm_consultant(id),
    application_no  VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    enrolment_no    VARCHAR(64)  NULL,
    confirmed_on    DATE         NULL,
    cancelled_on    DATE         NULL,
    cancel_reason   VARCHAR(500) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_placement_no UNIQUE (tenant_id, application_no),
    CONSTRAINT ck_crm_placement_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'OFFERED', 'CONFIRMED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
    )
);

CREATE INDEX idx_crm_placement_lead ON crm_placement (tenant_id, lead_id) WHERE deleted_at IS NULL;

CREATE TABLE crm_commercial_agreement (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)    NOT NULL,
    party           VARCHAR(32)    NOT NULL,
    institution_id  BIGINT         NULL REFERENCES crm_institution(id),
    course_id       BIGINT         NULL REFERENCES crm_course(id),
    session_id      BIGINT         NULL REFERENCES crm_admission_session(id),
    consultant_id   BIGINT         NULL REFERENCES crm_consultant(id),
    method          VARCHAR(32)    NOT NULL,
    rate            NUMERIC(14, 4) NOT NULL,
    base_kind       VARCHAR(32)    NOT NULL,
    trigger_code    VARCHAR(32)    NOT NULL,
    effective_from  DATE           NOT NULL,
    effective_to    DATE           NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ    NULL,
    CONSTRAINT ck_crm_agreement_party CHECK (party IN ('INSTITUTION', 'CONSULTANT')),
    CONSTRAINT ck_crm_agreement_method CHECK (method IN ('FIXED', 'PERCENT')),
    CONSTRAINT ck_crm_agreement_base CHECK (base_kind IN ('FEE', 'FIXED')),
    CONSTRAINT ck_crm_agreement_trigger CHECK (trigger_code IN ('ADMISSION', 'FEE_RECEIVED', 'COMMISSION_RECEIVED'))
);

CREATE TABLE crm_commercial_txn (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      VARCHAR(64)    NOT NULL,
    placement_id   BIGINT         NOT NULL REFERENCES crm_placement(id),
    consultant_id  BIGINT         NULL REFERENCES crm_consultant(id),
    ledger         VARCHAR(32)    NOT NULL,
    txn_kind       VARCHAR(32)    NOT NULL,
    amount         NUMERIC(14, 2) NOT NULL,
    counterparty   VARCHAR(32)    NULL,
    reference_no   VARCHAR(128)   NULL,
    txn_date       DATE           NOT NULL,
    note           VARCHAR(500)   NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ    NULL,
    CONSTRAINT ck_crm_txn_ledger CHECK (
        ledger IN ('STUDENT_FEE', 'INSTITUTION_COMMISSION', 'CONSULTANT_PAYABLE', 'EXPENSE')
    ),
    CONSTRAINT ck_crm_txn_kind CHECK (
        txn_kind IN ('SCHEDULE', 'RECEIPT', 'REFUND', 'EXPECTED', 'PAYMENT', 'ADJUSTMENT')
    ),
    CONSTRAINT ck_crm_txn_amount CHECK (amount >= 0)
);

CREATE UNIQUE INDEX uq_crm_txn_reference
    ON crm_commercial_txn (tenant_id, ledger, reference_no)
    WHERE reference_no IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE crm_import_batch (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    label           VARCHAR(256) NOT NULL,
    uploaded_by     VARCHAR(64)  NULL,
    row_count       INTEGER      NOT NULL,
    accepted_count  INTEGER      NOT NULL,
    rejected_count  INTEGER      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE crm_assignment_rule (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    source_code     VARCHAR(64) NULL,
    course_code     VARCHAR(64) NULL,
    city            VARCHAR(128) NULL,
    owner_user_id   VARCHAR(64) NOT NULL,
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
