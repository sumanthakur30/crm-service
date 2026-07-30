-- Phase 3b: scoring, calendar/calls/approvals, reports/ACL, inbound adapters.

-- Behavior scoring
CREATE TABLE crm_score_rule (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    points          INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    condition_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_score_rule UNIQUE (tenant_id, code)
);

CREATE INDEX idx_crm_score_rule_event ON crm_score_rule (tenant_id, event_type)
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE TABLE crm_score_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NOT NULL REFERENCES crm_lead (id),
    rule_id         BIGINT       NULL REFERENCES crm_score_rule (id),
    event_type      VARCHAR(64)  NOT NULL,
    points          INT          NOT NULL DEFAULT 0,
    summary         VARCHAR(256) NULL,
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_crm_score_event_lead ON crm_score_event (tenant_id, lead_id, created_at DESC);

-- Calendar / telephony / approvals
CREATE TABLE crm_calendar_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    related_type    VARCHAR(32)  NOT NULL,
    related_id      BIGINT       NOT NULL,
    title           VARCHAR(256) NOT NULL,
    starts_at       TIMESTAMPTZ  NOT NULL,
    ends_at         TIMESTAMPTZ  NULL,
    owner_user_id   VARCHAR(64)  NULL,
    location        VARCHAR(256) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED',
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_cal_related CHECK (related_type IN ('LEAD', 'OPPORTUNITY')),
    CONSTRAINT ck_crm_cal_status CHECK (status IN ('SCHEDULED', 'DONE', 'CANCELLED'))
);

CREATE INDEX idx_crm_cal_tenant_start ON crm_calendar_event (tenant_id, starts_at)
    WHERE deleted_at IS NULL;

CREATE TABLE crm_call_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    lead_id         BIGINT       NULL REFERENCES crm_lead (id),
    direction       VARCHAR(16)  NOT NULL DEFAULT 'OUTBOUND',
    phone           VARCHAR(64)  NULL,
    duration_sec    INT          NOT NULL DEFAULT 0,
    outcome         VARCHAR(64)  NULL,
    provider        VARCHAR(32)  NULL,
    provider_ref    VARCHAR(128) NULL,
    notes           TEXT         NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_call_dir CHECK (direction IN ('INBOUND', 'OUTBOUND', 'MISSED'))
);

CREATE INDEX idx_crm_call_lead ON crm_call_log (tenant_id, lead_id, occurred_at DESC);

CREATE TABLE crm_approval (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    object_id       BIGINT       NOT NULL,
    title           VARCHAR(256) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(64)  NULL,
    decided_by      VARCHAR(64)  NULL,
    decision_note   VARCHAR(512) NULL,
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_approval_obj CHECK (object_type IN ('OPPORTUNITY', 'QUOTATION', 'DISCOUNT')),
    CONSTRAINT ck_crm_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_crm_approval_pending ON crm_approval (tenant_id, status, created_at)
    WHERE status = 'PENDING';

-- Scheduled reports + field ACL
CREATE TABLE crm_report_schedule (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    report_type     VARCHAR(64)  NOT NULL,
    frequency       VARCHAR(16)  NOT NULL DEFAULT 'DAILY',
    recipients_json JSONB        NOT NULL DEFAULT '[]'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ  NULL,
    last_result_json JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_report_sched UNIQUE (tenant_id, code),
    CONSTRAINT ck_crm_report_freq CHECK (frequency IN ('HOURLY', 'DAILY', 'WEEKLY')),
    CONSTRAINT ck_crm_report_type CHECK (
        report_type IN ('FUNNEL', 'SOURCES', 'CAMPAIGNS', 'OVERDUE', 'FORECAST')
    )
);

CREATE TABLE crm_field_acl (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    role_code       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    field_name      VARCHAR(64)  NOT NULL,
    access          VARCHAR(16)  NOT NULL DEFAULT 'READ',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_field_acl UNIQUE (tenant_id, role_code, object_type, field_name),
    CONSTRAINT ck_crm_field_acl_access CHECK (access IN ('READ', 'WRITE', 'MASK', 'DENY')),
    CONSTRAINT ck_crm_field_acl_obj CHECK (object_type IN ('LEAD', 'OPPORTUNITY', 'QUOTATION', 'CONTACT'))
);

-- Inbound adapters (Meta / Google / missed-call / chatbot)
CREATE TABLE crm_inbound_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NULL,
    provider        VARCHAR(32)  NOT NULL,
    external_id     VARCHAR(128) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED',
    lead_id         BIGINT       NULL REFERENCES crm_lead (id),
    campaign_id     BIGINT       NULL REFERENCES crm_campaign (id),
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    error_message   VARCHAR(512) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_inbound_provider CHECK (
        provider IN ('META', 'GOOGLE', 'MISSED_CALL', 'CHATBOT')
    ),
    CONSTRAINT ck_crm_inbound_status CHECK (
        status IN ('RECEIVED', 'MAPPED', 'FAILED', 'DUPLICATE', 'SKIPPED')
    )
);

CREATE UNIQUE INDEX uq_crm_inbound_provider_ext
    ON crm_inbound_event (provider, external_id)
    WHERE external_id IS NOT NULL;
CREATE INDEX idx_crm_inbound_tenant ON crm_inbound_event (tenant_id, created_at DESC);
