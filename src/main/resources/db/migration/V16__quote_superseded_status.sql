-- Allow SUPERSEDED status for quote versioning.

ALTER TABLE crm_quotation DROP CONSTRAINT IF EXISTS ck_crm_quote_status;
ALTER TABLE crm_quotation
    ADD CONSTRAINT ck_crm_quote_status CHECK (
        status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED')
    );
