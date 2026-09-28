alter table drivers add column user_id bigint;
alter table drivers add column active bit not null default 1;
alter table drivers add column deactivated_at datetime(6);
alter table vehicles add column active bit not null default 1;
alter table vehicles add column deactivated_at datetime(6);
