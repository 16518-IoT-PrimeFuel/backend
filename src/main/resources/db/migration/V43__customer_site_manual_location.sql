-- User-selected points only; existing addresses are not backfilled from temporary geocoding.
ALTER TABLE customer_sites
    ADD COLUMN latitude DOUBLE NULL,
    ADD COLUMN longitude DOUBLE NULL,
    ADD COLUMN site_type VARCHAR(30) NULL;

ALTER TABLE customer_sites ADD CONSTRAINT chk_site_manual_location CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude IS NOT NULL AND longitude IS NOT NULL
        AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
        AND site_type IS NOT NULL)
);
ALTER TABLE customer_sites ADD CONSTRAINT chk_site_type CHECK (
    site_type IS NULL OR site_type IN ('STATION', 'TANK_LOCATION')
);
