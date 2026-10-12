create table admin_audit_log (
    id bigint not null auto_increment,
    actor_user_id bigint not null,
    action varchar(20) not null,
    target_user_id bigint not null,
    at datetime(6) not null,
    primary key (id)
) engine=InnoDB;
