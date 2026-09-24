CREATE TABLE valve_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id VARCHAR(160) NOT NULL UNIQUE,
    delivery_id BIGINT NOT NULL,
    desired_state VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    ack_id VARCHAR(160) UNIQUE,
    acknowledged_at TIMESTAMP
);
