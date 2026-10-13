-- One open scheduled follow-up per lead. SLA tasks use a different source and are not included.
CREATE UNIQUE INDEX uq_crm_task_open_lead_follow_up
    ON crm_task (tenant_id, related_type, related_id)
    WHERE source_code = 'LEAD_FOLLOW_UP'
      AND status = 'OPEN'
      AND deleted_at IS NULL;
