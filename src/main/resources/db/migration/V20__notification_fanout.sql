alter table notifications add column organization_id bigint;
alter table notifications add column event_id varchar(64);
alter table notifications add column channel VARCHAR(20) not null default 'IN_APP';
alter table notifications add column delivery_status VARCHAR(20) not null default 'DELIVERED';
alter table notifications add column attempts integer not null default 1;
alter table notifications add column last_attempt_at datetime(6);
alter table notifications add constraint uk_notifications_event_recipient_channel unique (event_id, user_id, channel);
