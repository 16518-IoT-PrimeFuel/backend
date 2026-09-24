CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL
);
