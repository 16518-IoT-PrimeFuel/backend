alter table transport_evidence_samples add column client_event_id varchar(160) null;
alter table transport_evidence_samples add constraint uk_tes_delivery_client_event unique (delivery_id, client_event_id);
