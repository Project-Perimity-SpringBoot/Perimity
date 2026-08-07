-- V2: Make purpose and host_user_id optional on visitor_requests table

ALTER TABLE visitor_requests ALTER COLUMN host_user_id DROP NOT NULL;
ALTER TABLE visitor_requests ALTER COLUMN purpose DROP NOT NULL;
