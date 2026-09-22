alter table deliveries add column physical_state VARCHAR(30);
alter table deliveries add column requested_volume float(53);
alter table deliveries add column delivered_volume float(53);
alter table deliveries add column started_at datetime(6);
alter table deliveries add column arrived_at datetime(6);
alter table deliveries add column delivering_at datetime(6);
alter table deliveries add column version integer not null default 0;
create table delivery_state_transitions (aggregate_version bigint not null, delivery_id bigint not null, id bigint not null auto_increment, occurred_at datetime(6) not null, from_state VARCHAR(30), to_state VARCHAR(30) not null, primary key (id)) engine=InnoDB;
