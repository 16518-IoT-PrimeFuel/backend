CREATE TABLE organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(30) NOT NULL,
    legacy_buyer_company_id BIGINT UNIQUE,
    legacy_provider_company_id BIGINT UNIQUE,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    role VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_membership_user_organization UNIQUE (user_id, organization_id)
);
