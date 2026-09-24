ALTER TABLE tanks ADD COLUMN legacy_equipment_id BIGINT;
CREATE UNIQUE INDEX uq_tanks_legacy_equipment ON tanks (legacy_equipment_id);

INSERT INTO customer_accounts (created_at, updated_at, name, organization_id, legacy_buyer_company_id, status)
SELECT b.created_at, b.updated_at, b.name, NULL, b.id, 'ACTIVE'
FROM buyer_companies b
WHERE NOT EXISTS (
    SELECT 1 FROM customer_accounts ca WHERE ca.legacy_buyer_company_id = b.id
);

INSERT INTO customer_sites (created_at, updated_at, customer_account_id, name, address, sector, status)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ca.id, 'Legacy default site',
       COALESCE(b.address, 'Legacy address'), b.sector, 'ACTIVE'
FROM customer_accounts ca
JOIN buyer_companies b ON b.id = ca.legacy_buyer_company_id
WHERE NOT EXISTS (
    SELECT 1 FROM customer_sites cs WHERE cs.customer_account_id = ca.id
);

INSERT INTO tanks (created_at, updated_at, customer_site_id, name, fuel_type, capacity, unit,
                   current_level, status, last_reading_at, legacy_equipment_id)
SELECT e.created_at, e.updated_at, cs.id, e.name, e.fuel_type, e.tank_capacity, 'L',
       e.current_level, COALESCE(e.status, 'ACTIVE'), NULL, e.id
FROM equipment e
JOIN customer_accounts ca ON ca.legacy_buyer_company_id = e.company_id
JOIN customer_sites cs ON cs.customer_account_id = ca.id
WHERE NOT EXISTS (
    SELECT 1 FROM tanks t WHERE t.legacy_equipment_id = e.id
);
