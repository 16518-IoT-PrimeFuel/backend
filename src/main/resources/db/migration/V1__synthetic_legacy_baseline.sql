-- Synthetic baseline generated from the current JPA model.
-- It is a migration contract, not proof of equivalence with the unavailable legacy database.

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(120) NOT NULL,
    company_id BIGINT,
    provider_id BIGINT
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE buyer_companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(150) NOT NULL,
    ruc VARCHAR(11) NOT NULL UNIQUE,
    sector VARCHAR(100),
    address VARCHAR(255),
    contact_email VARCHAR(150),
    phone VARCHAR(30)
);

CREATE TABLE provider_companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(150) NOT NULL,
    ruc VARCHAR(11) NOT NULL UNIQUE,
    rating DOUBLE,
    address VARCHAR(255),
    phone VARCHAR(30),
    description VARCHAR(500)
);

CREATE TABLE provider_company_fuel_types (
    provider_company_id BIGINT NOT NULL,
    fuel_type VARCHAR(20)
);

CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE fuel_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(255) NOT NULL,
    fuel_type VARCHAR(20) NOT NULL,
    price_per_unit DOUBLE NOT NULL,
    unit VARCHAR(255) NOT NULL,
    available_stock DOUBLE NOT NULL,
    capacity DOUBLE,
    provider_id BIGINT NOT NULL,
    active BOOLEAN
);

CREATE TABLE equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(255) NOT NULL,
    equipment_type VARCHAR(255) NOT NULL,
    license_plate VARCHAR(255) NOT NULL,
    fuel_type VARCHAR(20) NOT NULL,
    tank_capacity DOUBLE NOT NULL,
    current_level DOUBLE NOT NULL,
    location VARCHAR(255),
    status VARCHAR(255),
    auto_refill BOOLEAN,
    refill_threshold INTEGER,
    last_refill_date VARCHAR(255),
    company_id BIGINT NOT NULL,
    favorite_provider_id BIGINT
);

CREATE TABLE fuel_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    buyer_company_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    equipment_id BIGINT,
    fuel_product_id BIGINT NOT NULL,
    fuel_type VARCHAR(30) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity DOUBLE NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price DOUBLE NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    delivery_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(240)
);

CREATE TABLE fuel_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    request_id BIGINT UNIQUE,
    company_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    fuel_product_id BIGINT NOT NULL,
    equipment_id BIGINT,
    requested_quantity DOUBLE NOT NULL,
    total_price DOUBLE NOT NULL,
    status VARCHAR(30) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    scheduled_date DATE
);

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    order_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    amount DOUBLE NOT NULL,
    status VARCHAR(255) NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    transaction_reference VARCHAR(255),
    paid_at TIMESTAMP
);

CREATE TABLE drivers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    provider_id BIGINT NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    license_number VARCHAR(60) NOT NULL UNIQUE,
    phone_number VARCHAR(30) NOT NULL,
    email VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    provider_id BIGINT NOT NULL,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(80) NOT NULL,
    capacity DOUBLE NOT NULL,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL
);

CREATE TABLE deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    order_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    dispatched_at TIMESTAMP,
    delivered_at TIMESTAMP,
    scheduled_date VARCHAR(255),
    notes TEXT
);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL,
    reference_id BIGINT
);

CREATE TABLE provider_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    company_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    CONSTRAINT uq_provider_rating_company_provider UNIQUE (company_id, provider_id)
);
