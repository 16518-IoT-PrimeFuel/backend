CREATE TABLE delivery_geofences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    radius_meters DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE delivery_journal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(160) NOT NULL UNIQUE,
    delivery_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    payload TEXT NOT NULL
);
