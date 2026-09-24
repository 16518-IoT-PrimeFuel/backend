-- Test-only reference data. Never deploy this fixture to a shared environment.
INSERT INTO roles (id, name) VALUES (1, 'ROLE_BUYER'), (2, 'ROLE_PROVIDER');
INSERT INTO buyer_companies (id, created_at, updated_at, name, ruc, sector, address, contact_email, phone)
VALUES (1, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00',
        'Mock Buyer S.A.C.', '20123456789', 'Transport', 'Av. Demo 100', 'buyer@example.test', '+51 900 000 001');
INSERT INTO provider_companies (id, created_at, updated_at, name, ruc, rating, address, phone, description)
VALUES (1, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00',
        'Mock Fuel Provider S.A.C.', '20987654321', 4.8, 'Av. Demo 200', '+51 900 000 002', 'Synthetic provider for migration tests');
INSERT INTO fuel_products (id, created_at, updated_at, name, fuel_type, price_per_unit, unit, available_stock, capacity, provider_id, active)
VALUES (1, TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00',
        'Diesel B5', 'DIESEL', 14.50, 'L', 12000, 12000, 1, TRUE);
