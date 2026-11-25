-- Allow a stage rule to enroll a sequence. Java already accepted ENROLL_SEQUENCE;
-- the V14 check rejected the insert.

ALTER TABLE crm_stage_automation DROP CONSTRAINT IF EXISTS ck_crm_stage_auto_action;

ALTER TABLE crm_stage_automation
    ADD CONSTRAINT ck_crm_stage_auto_action
    CHECK (action_type IN ('CREATE_TASK', 'TIMELINE_NOTE', 'ENROLL_SEQUENCE'));
