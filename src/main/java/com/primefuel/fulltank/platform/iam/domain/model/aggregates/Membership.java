package com.primefuel.fulltank.platform.iam.domain.model.aggregates;

import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Membership extends AbstractDomainAggregateRoot<Membership> {

    private Long id;
    private Long organizationId;
    private Long userId;
    private MembershipRole role;
    private boolean active = true;

    public Membership(GrantMembershipCommand command) {
        this.organizationId = command.organizationId();
        this.userId = command.userId();
        this.role = command.role();
        this.active = true;
    }

    public void revoke() {
        this.active = false;
    }

    public void reactivate(MembershipRole newRole) {
        this.role = newRole;
        this.active = true;
    }
}
