-- fuel_orders.request_id was never written; the order<->request link lived only in replenishment_requests.order_id.
-- Backfill it. Safe against uk on request_id (V1): only NULL rows, only when no other order already holds that
-- request id (legacy values may point at the dropped fuel_requests ids and are left untouched).
update fuel_orders o
  join replenishment_requests r on r.order_id = o.id
   set o.request_id = r.id
 where o.request_id is null
   and not exists (select 1 from (select request_id from fuel_orders where request_id is not null) x
                    where x.request_id = r.id);
