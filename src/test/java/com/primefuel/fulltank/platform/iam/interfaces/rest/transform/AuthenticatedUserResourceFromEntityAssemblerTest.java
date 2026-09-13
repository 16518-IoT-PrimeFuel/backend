package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.domain.model.entities.Role;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.Roles;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticatedUserResourceFromEntityAssemblerTest {

    @Test
    void includesRoleAndBusinessIdentifiers() {
        var user = new User("provider", "hash", List.of(new Role(Roles.ROLE_PROVIDER)), 12L, 34L);

        var resource = AuthenticatedUserResourceFromEntityAssembler.toResourceFromEntity(user, "jwt");

        assertEquals(List.of("ROLE_PROVIDER"), resource.roles());
        assertEquals(12L, resource.companyId());
        assertEquals(34L, resource.providerId());
    }
}
