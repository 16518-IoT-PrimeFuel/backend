CREATE TABLE delivery_business_journal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_event_id VARCHAR(36) NOT NULL,
    delivery_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    summary VARCHAR(200) NOT NULL,
    ref_id VARCHAR(80) NOT NULL,
    CONSTRAINT pk_delivery_business_journal PRIMARY KEY (id),
    CONSTRAINT uk_delivery_business_journal_source_event UNIQUE (source_event_id),
    INDEX ix_delivery_business_journal_delivery_time (delivery_id, occurred_at)
);
