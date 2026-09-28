-- PORT-1: backfill pre-T04 users into organizations + OWNER memberships (S04/S05 rules).
-- * One organization per legacy company: buyer_companies -> CUSTOMER, provider_companies -> DISTRIBUTOR.
--   Skipped: blank RUC, RUC already owned by an organization (pre-existing/onboarded, RUC unverified),
--   and RUC present in BOTH buyer_companies and provider_companies (ambiguous).
-- * OWNER membership only to an organization created by THIS migration (id > @v23_max_org_id), and only
--   for users with no membership yet and a single legacy link (company_id XOR provider_id).
-- Everything skipped is left for manual decision (zero ambiguous assignments). Idempotent: NOT EXISTS guards.
SET @v23_max_org_id = (SELECT COALESCE(MAX(id), 0) FROM organizations);

INSERT INTO organizations (active, created_at, updated_at, name, ruc, type)
SELECT 1, b.created_at, b.updated_at, b.name, b.ruc, 'CUSTOMER'
FROM buyer_companies b
WHERE b.ruc <> ''
  AND NOT EXISTS (SELECT 1 FROM organizations o WHERE o.ruc = b.ruc)
  AND NOT EXISTS (SELECT 1 FROM provider_companies p WHERE p.ruc = b.ruc);

INSERT INTO organizations (active, created_at, updated_at, name, ruc, type)
SELECT 1, p.created_at, p.updated_at, p.name, p.ruc, 'DISTRIBUTOR'
FROM provider_companies p
WHERE p.ruc <> ''
  AND NOT EXISTS (SELECT 1 FROM organizations o WHERE o.ruc = p.ruc)
  AND NOT EXISTS (SELECT 1 FROM buyer_companies b WHERE b.ruc = p.ruc);

INSERT INTO memberships (active, created_at, updated_at, organization_id, user_id, role)
SELECT 1, u.created_at, u.created_at, o.id, u.id, 'OWNER'
FROM users u
JOIN buyer_companies b ON b.id = u.company_id
JOIN organizations o ON o.ruc = b.ruc AND o.type = 'CUSTOMER' AND o.id > @v23_max_org_id
WHERE u.provider_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = u.id);

INSERT INTO memberships (active, created_at, updated_at, organization_id, user_id, role)
SELECT 1, u.created_at, u.created_at, o.id, u.id, 'OWNER'
FROM users u
JOIN provider_companies p ON p.id = u.provider_id
JOIN organizations o ON o.ruc = p.ruc AND o.type = 'DISTRIBUTOR' AND o.id > @v23_max_org_id
WHERE u.company_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = u.id);
