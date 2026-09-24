INSERT INTO organizations (created_at, updated_at, name, type, legacy_buyer_company_id, legacy_provider_company_id, status)
SELECT b.created_at, b.updated_at, b.name, 'BUYER', b.id, NULL, 'ACTIVE'
FROM buyer_companies b
WHERE NOT EXISTS (
    SELECT 1 FROM organizations o WHERE o.legacy_buyer_company_id = b.id
);

INSERT INTO organizations (created_at, updated_at, name, type, legacy_buyer_company_id, legacy_provider_company_id, status)
SELECT p.created_at, p.updated_at, p.name, 'PROVIDER', NULL, p.id, 'ACTIVE'
FROM provider_companies p
WHERE NOT EXISTS (
    SELECT 1 FROM organizations o WHERE o.legacy_provider_company_id = p.id
);

INSERT INTO memberships (created_at, updated_at, user_id, organization_id, role, status)
SELECT u.created_at, u.updated_at, u.id, o.id, 'OWNER', 'ACTIVE'
FROM users u
JOIN organizations o ON o.legacy_buyer_company_id = u.company_id
WHERE NOT EXISTS (
    SELECT 1 FROM memberships m WHERE m.user_id = u.id AND m.organization_id = o.id
);

INSERT INTO memberships (created_at, updated_at, user_id, organization_id, role, status)
SELECT u.created_at, u.updated_at, u.id, o.id, 'OWNER', 'ACTIVE'
FROM users u
JOIN organizations o ON o.legacy_provider_company_id = u.provider_id
WHERE NOT EXISTS (
    SELECT 1 FROM memberships m WHERE m.user_id = u.id AND m.organization_id = o.id
);
