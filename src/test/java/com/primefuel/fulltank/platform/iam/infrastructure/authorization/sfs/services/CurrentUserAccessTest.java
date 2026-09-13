package com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services;

import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserAccessTest {

    private final CurrentUserAccess access = new CurrentUserAccess();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountOwnerCanAccessOnlyTheirCompany() {
        authenticate(7L, 31L, null, "ROLE_BUYER");

        assertTrue(access.ownsCompany(31L));
        assertFalse(access.ownsCompany(32L));
        assertFalse(access.ownsProvider(31L));
    }

    @Test
    void providerOwnerCannotActAsBuyer() {
        authenticate(8L, null, 41L, "ROLE_PROVIDER");

        assertTrue(access.ownsProvider(41L));
        assertFalse(access.ownsProvider(42L));
        assertFalse(access.ownsCompany(31L));
    }

    private static void authenticate(Long userId, Long companyId, Long providerId, String role) {
        var principal = new UserDetailsImpl(userId, "test", "encoded", companyId, providerId,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
