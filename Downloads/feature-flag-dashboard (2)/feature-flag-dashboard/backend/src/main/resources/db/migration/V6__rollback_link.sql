-- A rollback is a new audit entry that points at the entry whose change it undoes. Nothing is edited or deleted.
ALTER TABLE audit_log ADD COLUMN rollback_of UUID;
