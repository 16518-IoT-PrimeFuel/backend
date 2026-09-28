-- Detectar antes del cambio si existen órdenes con más de una entrega; la migración no elimina datos:
-- SELECT order_id, COUNT(*) FROM deliveries GROUP BY order_id HAVING COUNT(*) > 1;
ALTER TABLE deliveries
    ADD CONSTRAINT uk_deliveries_order_id UNIQUE (order_id);
