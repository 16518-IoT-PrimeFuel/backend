CREATE TABLE device_bindings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    device_id VARCHAR(160) NOT NULL,
    channel VARCHAR(80) NOT NULL,
    tank_id BIGINT NOT NULL,
    buyer_company_id BIGINT NOT NULL,
    credential_hash VARCHAR(128) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_device_binding_interval UNIQUE (device_id, channel, valid_from)
);

CREATE INDEX ix_device_binding_lookup
    ON device_bindings (device_id, channel, valid_from, valid_until, status);

CREATE TABLE telemetry_readings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    event_id VARCHAR(160) NOT NULL UNIQUE,
    device_id VARCHAR(160) NOT NULL,
    channel VARCHAR(80) NOT NULL,
    sequence_number BIGINT NOT NULL,
    tank_id BIGINT NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    level_value DOUBLE NOT NULL,
    unit VARCHAR(20) NOT NULL,
    quality VARCHAR(20) NOT NULL,
    CONSTRAINT uq_telemetry_device_sequence UNIQUE (device_id, channel, sequence_number)
);

CREATE INDEX ix_telemetry_tank_captured
    ON telemetry_readings (tank_id, captured_at);
