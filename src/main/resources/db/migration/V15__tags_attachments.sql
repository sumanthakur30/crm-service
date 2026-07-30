-- Tags + soft attachments metadata (URL/stub storage).

CREATE TABLE IF NOT EXISTS crm_tag (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    color           VARCHAR(16)  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT uq_crm_tag_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS crm_object_tag (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    object_id       BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL REFERENCES crm_tag (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_crm_object_tag UNIQUE (tenant_id, object_type, object_id, tag_id),
    CONSTRAINT ck_crm_object_tag_type CHECK (object_type IN ('LEAD', 'OPPORTUNITY', 'ACCOUNT', 'QUOTATION'))
);

CREATE INDEX IF NOT EXISTS idx_crm_object_tag_obj
    ON crm_object_tag (tenant_id, object_type, object_id);

CREATE TABLE IF NOT EXISTS crm_attachment (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    object_type     VARCHAR(32)  NOT NULL,
    object_id       BIGINT       NOT NULL,
    file_name       VARCHAR(256) NOT NULL,
    content_type    VARCHAR(128) NULL,
    storage_url     VARCHAR(1024) NULL,
    size_bytes      BIGINT       NULL,
    note            VARCHAR(512) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ  NULL,
    CONSTRAINT ck_crm_attachment_type CHECK (object_type IN ('LEAD', 'OPPORTUNITY', 'ACCOUNT', 'QUOTATION'))
);

CREATE INDEX IF NOT EXISTS idx_crm_attachment_obj
    ON crm_attachment (tenant_id, object_type, object_id)
    WHERE deleted_at IS NULL;
