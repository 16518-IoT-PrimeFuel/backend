package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.ports.TenantMembershipCommandStore;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantMembershipCommandService {
    private final UserRepository users;
    private final TenantMembershipCommandStore memberships;

    public TenantMembershipCommandService(UserRepository users, TenantMembershipCommandStore memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional
    public Long invite(Long providerId, String username, String role) {
        var user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        memberships.activate(providerId, user.getId(), role);
        return user.getId();
    }

    @Transactional
    public boolean revoke(Long providerId, Long userId) {
        return memberships.revoke(providerId, userId);
    }
}
