-- Phase 1 UX: notes + timeline events on CRM objects (leads first).

CREATE TABLE crm_note (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    related_type    VARCHAR(32)  NOT NULL DEFAULT 'LEAD',
    related_id      BIGINT       NOT NULL,
    body            TEXT         NOT NULL,
    author_user_id  VARCHAR(64)  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_note_related CHECK (related_type IN ('LEAD', 'OPPORTUNITY', 'ACCOUNT', 'CONTACT'))
);

CREATE INDEX idx_crm_note_related ON crm_note (tenant_id, related_type, related_id)
    WHERE deleted_at IS NULL;

CREATE TABLE crm_timeline_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    related_type    VARCHAR(32)  NOT NULL DEFAULT 'LEAD',
    related_id      BIGINT       NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    summary         VARCHAR(512) NOT NULL,
    payload_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    actor_user_id   VARCHAR(64)  NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_crm_timeline_related CHECK (related_type IN ('LEAD', 'OPPORTUNITY', 'ACCOUNT', 'CONTACT'))
);

CREATE INDEX idx_crm_timeline_related ON crm_timeline_event (tenant_id, related_type, related_id, occurred_at DESC);
