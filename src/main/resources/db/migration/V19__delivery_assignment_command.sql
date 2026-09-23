alter table deliveries add column assignment_command_id varchar(120);
alter table deliveries add constraint uk_deliveries_assignment_command_id unique (assignment_command_id);
