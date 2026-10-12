-- Un pago vigente por orden: active_order_id = order_id salvo pagos FAILED (NULL no colisiona en MySQL),
-- igual que deliveries.active_order_id (V40). operation_number: numero de operacion bancaria (US-08), unico.
ALTER TABLE payments
    ADD COLUMN active_order_id bigint NULL,
    ADD COLUMN operation_number varchar(20) NULL,
    ADD COLUMN currency varchar(3) NOT NULL DEFAULT 'PEN';

-- Si historicamente hubiera duplicados, solo el ultimo pago no FAILED de cada orden queda vigente.
UPDATE payments p
SET p.active_order_id = p.order_id
WHERE p.status <> 'FAILED'
  AND p.id = (SELECT m.max_id
              FROM (SELECT order_id, MAX(id) AS max_id FROM payments WHERE status <> 'FAILED' GROUP BY order_id) m
              WHERE m.order_id = p.order_id);

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_active_order_id UNIQUE (active_order_id),
    ADD CONSTRAINT uk_payments_operation_number UNIQUE (operation_number);
