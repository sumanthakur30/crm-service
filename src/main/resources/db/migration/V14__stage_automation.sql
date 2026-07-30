-- Stage-change automation rules (CRM-local; optional School rule-engine evaluate).

CREATE TABLE IF NOT EXISTS crm_stage_automation (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    to_stage_code   VARCHAR(64)  NULL,
    to_stage_id     BIGINT       NULL,
    action_type     VARCHAR(32)  NOT NULL,
    action_config   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_stage_auto_object CHECK (object_type IN ('LEAD', 'OPPORTUNITY')),
    CONSTRAINT ck_crm_stage_auto_action CHECK (action_type IN ('CREATE_TASK', 'TIMELINE_NOTE'))
);

CREATE INDEX IF NOT EXISTS idx_crm_stage_auto_tenant_obj
    ON crm_stage_automation (tenant_id, object_type, active)
    WHERE deleted_at IS NULL;
