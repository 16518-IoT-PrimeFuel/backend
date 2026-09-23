create table geofence_policies (
  id bigint not null auto_increment,
  delivery_id bigint not null,
  provider_id bigint not null,
  center_latitude float(53) not null,
  center_longitude float(53) not null,
  radius_meters float(53) not null,
  policy_version integer not null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id)
) engine=InnoDB;
alter table geofence_policies add constraint uk_geofence_policies_delivery_version unique (delivery_id, policy_version);

create table safety_decisions (
  id bigint not null auto_increment,
  delivery_id bigint not null,
  provider_id bigint not null,
  policy_id bigint,
  policy_version integer,
  authorized bit not null,
  reason varchar(30),
  tracking_evidence_id bigint,
  observed_at datetime(6),
  evaluated_at datetime(6) not null,
  distance_meters float(53),
  accuracy_meters float(53),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id)
) engine=InnoDB;
create index ix_safety_decisions_delivery on safety_decisions (delivery_id, evaluated_at);
