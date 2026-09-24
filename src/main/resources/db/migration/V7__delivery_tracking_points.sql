CREATE TABLE delivery_tracking_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(160) NOT NULL UNIQUE,
    delivery_id BIGINT NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    speed_kph DOUBLE
);
