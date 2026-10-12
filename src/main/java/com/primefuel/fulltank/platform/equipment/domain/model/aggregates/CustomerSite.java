package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.SiteType;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomerSite extends AbstractDomainAggregateRoot<CustomerSite> {

    private Long id;
    private Long organizationId;
    private Long customerAccountId;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private SiteType siteType;
    private boolean active = true;

    public CustomerSite(RegisterSiteCommand command) {
        this.organizationId = command.organizationId();
        this.customerAccountId = command.customerAccountId();
        this.name = command.name();
        this.address = command.address();
        this.latitude = command.latitude();
        this.longitude = command.longitude();
        this.siteType = command.siteType();
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
