-- Read-only report. Run it against an authorized MySQL snapshot/database.
-- It intentionally does not mutate data.
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
    'organizations', 'memberships', 'customer_accounts', 'customer_sites', 'tanks',
    'device_bindings', 'telemetry_readings', 'telemetry_inbox', 'telemetry_checkpoints',
    'refill_policies', 'refill_episodes', 'replenishment_request_lifecycle', 'outbox_events'
  )
ORDER BY table_name, ordinal_position;

SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT
  (SELECT COUNT(*) FROM buyer_companies) AS legacy_buyer_companies,
  (SELECT COUNT(*) FROM customer_accounts WHERE legacy_buyer_company_id IS NOT NULL) AS mapped_customer_accounts,
  (SELECT COUNT(*) FROM equipment) AS legacy_equipment,
  (SELECT COUNT(*) FROM tanks WHERE legacy_equipment_id IS NOT NULL) AS mapped_tanks,
  (SELECT COUNT(*) FROM users) AS legacy_users,
  (SELECT COUNT(*) FROM memberships) AS memberships;
