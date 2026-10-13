-- One open scheduled follow-up per deal. Lead follow-ups use a different source code.
CREATE UNIQUE INDEX uq_crm_task_open_opportunity_follow_up
    ON crm_task (tenant_id, related_type, related_id)
    WHERE source_code = 'OPPORTUNITY_FOLLOW_UP'
      AND status = 'OPEN'
      AND deleted_at IS NULL;
