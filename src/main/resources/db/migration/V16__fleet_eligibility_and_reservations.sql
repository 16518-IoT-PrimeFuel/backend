ALTER TABLE drivers ADD COLUMN license_expires_at DATE NULL;
ALTER TABLE vehicles ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE fleet_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    delivery_id BIGINT NULL,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    volume DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP NULL,
    CONSTRAINT ck_fleet_reservation_window CHECK (window_end > window_start),
    CONSTRAINT ck_fleet_reservation_volume CHECK (volume > 0)
);

CREATE INDEX ix_fleet_reservation_driver_window
    ON fleet_reservations (driver_id, window_start, window_end, status);
CREATE INDEX ix_fleet_reservation_vehicle_window
    ON fleet_reservations (vehicle_id, window_start, window_end, status);
