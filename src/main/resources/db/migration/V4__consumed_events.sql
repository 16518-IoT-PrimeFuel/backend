create table consumed_events (created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, consumer varchar(80) not null, event_id varchar(36) not null, primary key (id)) engine=InnoDB;
alter table consumed_events add constraint uk_consumed_events_consumer_event unique (consumer, event_id);
