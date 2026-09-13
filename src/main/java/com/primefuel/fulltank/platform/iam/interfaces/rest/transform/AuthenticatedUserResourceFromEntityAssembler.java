package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.AuthenticatedUserResource;

public final class AuthenticatedUserResourceFromEntityAssembler {

    private AuthenticatedUserResourceFromEntityAssembler() {
    }

    public static AuthenticatedUserResource toResourceFromEntity(User user, String token) {
        var roles = user.getRoles() == null ? java.util.List.<String>of()
                : user.getRoles().stream().map(role -> role.getName().name()).toList();
        return new AuthenticatedUserResource(user.getId(), user.getUsername(), token,
                roles, user.getCompanyId(), user.getProviderId());
    }
}
