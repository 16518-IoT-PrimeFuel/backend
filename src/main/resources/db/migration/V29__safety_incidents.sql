CREATE TABLE safety_incidents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    kind VARCHAR(30) NOT NULL,
    command_id VARCHAR(36) NULL,
    occurred_at DATETIME(6) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_safety_incidents PRIMARY KEY (id),
    INDEX ix_safety_incidents_delivery_time (delivery_id, occurred_at)
);
