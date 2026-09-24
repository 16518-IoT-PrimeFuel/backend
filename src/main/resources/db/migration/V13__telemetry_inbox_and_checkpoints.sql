CREATE TABLE telemetry_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    event_id VARCHAR(160) NOT NULL UNIQUE,
    device_id VARCHAR(160) NOT NULL,
    channel VARCHAR(80) NOT NULL,
    sequence_number BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    last_error VARCHAR(255),
    received_at TIMESTAMP NOT NULL
);

CREATE TABLE telemetry_checkpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    device_id VARCHAR(160) NOT NULL,
    channel VARCHAR(80) NOT NULL,
    last_sequence_number BIGINT NOT NULL,
    CONSTRAINT uq_telemetry_checkpoint_device UNIQUE (device_id, channel)
);
