create table customer_mapping_quarantines (created_at datetime(6) not null, id bigint not null auto_increment, legacy_company_id bigint not null, updated_at datetime(6) not null, reason varchar(255) not null, ruc varchar(11), primary key (id)) engine=InnoDB;
alter table customer_mapping_quarantines add constraint uk_customer_mapping_quarantine_legacy_company unique (legacy_company_id);
