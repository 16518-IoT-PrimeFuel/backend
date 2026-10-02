create table provider_buyer_links (
    id bigint not null auto_increment,
    provider_id bigint not null,
    buyer_company_id bigint not null,
    organization_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_provider_buyer_link unique (provider_id, buyer_company_id),
    constraint fk_provider_buyer_link_provider foreign key (provider_id) references provider_companies(id),
    constraint fk_provider_buyer_link_buyer foreign key (buyer_company_id) references buyer_companies(id),
    constraint fk_provider_buyer_link_org foreign key (organization_id) references organizations(id)
) engine=InnoDB;
