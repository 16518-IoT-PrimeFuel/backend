ALTER TABLE notifications ADD COLUMN source_event_key VARCHAR(160) NULL;
CREATE UNIQUE INDEX uq_notification_source_event ON notifications (source_event_key);
