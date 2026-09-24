CREATE TABLE replenishment_request_lifecycle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    request_id BIGINT NOT NULL UNIQUE,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    state VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    consumed_order_id BIGINT NULL,
    consumed_at TIMESTAMP NULL
);
