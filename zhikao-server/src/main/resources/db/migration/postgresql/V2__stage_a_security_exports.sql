ALTER TABLE sys_user ADD COLUMN password_change_required INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN password_expires_at TIMESTAMP;
ALTER TABLE sys_export_task ADD COLUMN lease_owner VARCHAR(64);
ALTER TABLE sys_export_task ADD COLUMN lease_until TIMESTAMP;
ALTER TABLE sys_export_task ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_export_pending ON sys_export_task(status, lease_until);
