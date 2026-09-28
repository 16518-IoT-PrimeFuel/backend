create table delivery_tracking (
  id bigint not null auto_increment,
  delivery_id bigint not null,
  provider_id bigint not null,
  driver_id bigint,
  last_latitude float(53),
  last_longitude float(53),
  last_accuracy_meters float(53),
  last_position_at datetime(6),
  last_position_evidence_id bigint,
  loaded bit not null default 0,
  last_load_milestone varchar(20),
  last_load_at datetime(6),
  last_load_volume float(53),
  last_load_unit varchar(20),
  last_load_evidence_id bigint,
  version integer not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id)
) engine=InnoDB;
alter table delivery_tracking add constraint uk_delivery_tracking_delivery unique (delivery_id);

create table transport_evidence_samples (
  id bigint not null auto_increment,
  delivery_id bigint not null,
  provider_id bigint not null,
  driver_id bigint,
  kind varchar(20) not null,
  latitude float(53),
  longitude float(53),
  accuracy_meters float(53),
  milestone varchar(20),
  volume float(53),
  unit varchar(20),
  recorded_at datetime(6) not null,
  received_at datetime(6) not null,
  latest_advanced bit not null default 0,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id)
) engine=InnoDB;
create index ix_transport_evidence_samples_delivery on transport_evidence_samples (delivery_id, received_at);
