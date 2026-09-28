package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.OrganizationMembershipResource;

public final class AuthenticatedUserResourceFromEntityAssembler {

    private AuthenticatedUserResourceFromEntityAssembler() {
    }

    public static AuthenticatedUserResource toResourceFromEntity(User user, String token,
                                                                 java.util.List<Membership> memberships) {
        var roles = user.getRoles() == null ? java.util.List.<String>of()
                : user.getRoles().stream().map(role -> role.getName().name()).toList();
        var membershipResources = memberships == null ? java.util.List.<OrganizationMembershipResource>of()
                : memberships.stream()
                        .map(membership -> new OrganizationMembershipResource(
                                membership.getOrganizationId(),
                                membership.getRole() == null ? null : membership.getRole().name()))
                        .toList();
        return new AuthenticatedUserResource(user.getId(), user.getUsername(), token,
                roles, user.getCompanyId(), user.getProviderId(), membershipResources);
    }
}
