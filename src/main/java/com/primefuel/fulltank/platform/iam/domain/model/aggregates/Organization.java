package com.primefuel.fulltank.platform.iam.domain.model.aggregates;

import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Organization extends AbstractDomainAggregateRoot<Organization> {

    private Long id;
    private String name;
    private String ruc;
    private OrganizationType type;
    private boolean active = true;

    public Organization(CreateOrganizationCommand command) {
        this.name = command.name();
        this.ruc = command.ruc();
        this.type = command.type();
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
