-- Phase 1b: assignment members + round-robin cursor (additive).

CREATE TABLE crm_team_member (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    team_id         VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
    user_id         VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_team_member UNIQUE (tenant_id, team_id, user_id)
);

CREATE INDEX idx_crm_team_member_tenant ON crm_team_member (tenant_id, team_id)
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE TABLE crm_rr_cursor (
    tenant_id       VARCHAR(64)  NOT NULL,
    team_id         VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
    last_index      INT          NOT NULL DEFAULT -1,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, team_id)
);
