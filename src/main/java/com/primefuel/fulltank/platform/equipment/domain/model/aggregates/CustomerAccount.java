package com.primefuel.fulltank.platform.equipment.domain.model.aggregates;

import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomerAccount extends AbstractDomainAggregateRoot<CustomerAccount> {

    private Long id;
    private Long organizationId;
    private String name;
    private String ruc;
    private String address;
    private String contactEmail;
    private String phone;
    private Long legacyCompanyId;
    private boolean active = true;

    public CustomerAccount(RegisterCustomerCommand command) {
        this.organizationId = command.organizationId();
        this.name = command.name();
        this.ruc = command.ruc();
        this.address = command.address();
        this.contactEmail = command.contactEmail();
        this.phone = command.phone();
        this.legacyCompanyId = command.legacyCompanyId();
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
