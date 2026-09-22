package com.primefuel.fulltank.platform.iam.domain.model.aggregates;

import com.primefuel.fulltank.platform.iam.domain.model.commands.InviteMemberCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class OrganizationInvitation extends AbstractDomainAggregateRoot<OrganizationInvitation> {

    private Long id;
    private Long organizationId;
    private String email;
    private MembershipRole role;
    private String token;
    private InvitationStatus status;
    private Instant expiresAt;
    private Long invitedByUserId;

    public OrganizationInvitation(InviteMemberCommand command, String token, Instant expiresAt) {
        this.organizationId = command.organizationId();
        this.email = command.email();
        this.role = command.role();
        this.invitedByUserId = command.invitedByUserId();
        this.token = token;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now) {
        return isPending() && !isExpired(now);
    }

    public void accept() {
        this.status = InvitationStatus.ACCEPTED;
    }

    public void revoke() {
        this.status = InvitationStatus.REVOKED;
    }
}
