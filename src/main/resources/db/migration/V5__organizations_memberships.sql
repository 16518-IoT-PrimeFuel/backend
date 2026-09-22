create table organizations (active bit not null, created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, name varchar(150) not null, ruc varchar(11) not null, type VARCHAR(20) not null, primary key (id)) engine=InnoDB;
create table memberships (active bit not null, created_at datetime(6) not null, id bigint not null auto_increment, organization_id bigint not null, updated_at datetime(6) not null, user_id bigint not null, role VARCHAR(20) not null, primary key (id)) engine=InnoDB;
alter table organizations add constraint uk_organizations_ruc unique (ruc);
alter table memberships add constraint uk_memberships_organization_user unique (organization_id, user_id);
