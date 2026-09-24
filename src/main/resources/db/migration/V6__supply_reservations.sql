CREATE TABLE supply_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE,
    fuel_product_id BIGINT NOT NULL,
    quantity DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    reserved_at TIMESTAMP NOT NULL
);
