UPDATE replenishment_requests r
JOIN fuel_requests f ON r.episode_key = CONCAT('fuel-request:', f.id)
SET r.delivery_address = f.delivery_address,
    r.delivery_date = f.delivery_date
WHERE r.delivery_address IS NULL OR r.delivery_date IS NULL;

DROP TABLE fuel_requests;
