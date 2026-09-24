CREATE TABLE customer_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(150) NOT NULL,
    organization_id BIGINT,
    legacy_buyer_company_id BIGINT UNIQUE,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE customer_sites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    customer_account_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    sector VARCHAR(100),
    status VARCHAR(20) NOT NULL
);

CREATE TABLE tanks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    customer_site_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    fuel_type VARCHAR(20) NOT NULL,
    capacity DOUBLE NOT NULL,
    unit VARCHAR(20) NOT NULL,
    current_level DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_reading_at TIMESTAMP
);
