CREATE TABLE refill_policies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    tank_id BIGINT NOT NULL UNIQUE,
    buyer_company_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    fuel_product_id BIGINT NOT NULL,
    threshold_value DOUBLE NOT NULL,
    hysteresis_value DOUBLE NOT NULL,
    target_volume DOUBLE NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE refill_episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    episode_key VARCHAR(180) NOT NULL UNIQUE,
    tank_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    request_id BIGINT NULL
);

CREATE INDEX ix_refill_episode_tank_status ON refill_episodes (tank_id, status);
