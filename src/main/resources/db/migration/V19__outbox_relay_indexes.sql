CREATE INDEX ix_outbox_status_occurred ON outbox_events (status, occurred_at);
