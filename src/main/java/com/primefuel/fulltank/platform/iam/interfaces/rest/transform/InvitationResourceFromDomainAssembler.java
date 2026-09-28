package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.InvitationResource;

public final class InvitationResourceFromDomainAssembler {

    private InvitationResourceFromDomainAssembler() {
    }

    public static InvitationResource toResourceFromDomain(OrganizationInvitation invitation) {
        return new InvitationResource(
                invitation.getId(),
                invitation.getOrganizationId(),
                invitation.getEmail(),
                invitation.getRole() == null ? null : invitation.getRole().name(),
                invitation.getStatus() == null ? null : invitation.getStatus().name(),
                invitation.getToken(),
                invitation.getExpiresAt() == null ? null : invitation.getExpiresAt().toString());
    }
}
