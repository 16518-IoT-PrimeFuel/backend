create table fleet_reservations (
  id bigint not null auto_increment,
  provider_id bigint not null,
  driver_id bigint not null,
  tanker_id bigint not null,
  reference varchar(120),
  window_start datetime(6) not null,
  window_end datetime(6) not null,
  volume float(53) not null,
  unit varchar(20) not null,
  status varchar(20) not null,
  version integer not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id)
) engine=InnoDB;
alter table fleet_reservations add constraint uk_fleet_reservations_reference unique (reference);
create index ix_fleet_reservations_resource_window on fleet_reservations (provider_id, driver_id, tanker_id, status, window_start);
