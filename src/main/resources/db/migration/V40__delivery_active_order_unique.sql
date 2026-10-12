-- Una entrega FAILED/CANCELLED libera la orden para reasignarla. uk_deliveries_order_id (V35) lo impedia:
-- se reemplaza por un unico sobre active_order_id, NULL en entregas liberadas (los NULL no colisionan en MySQL).
ALTER TABLE deliveries ADD COLUMN active_order_id bigint NULL;

UPDATE deliveries SET active_order_id = order_id WHERE status <> 'FAILED';

DROP INDEX uk_deliveries_order_id ON deliveries;

ALTER TABLE deliveries
    ADD CONSTRAINT uk_deliveries_active_order_id UNIQUE (active_order_id);
