package com.primefuel.fulltank.platform.iam.application.ports;

public interface TenantMembershipCommandStore {
    boolean activate(Long providerId, Long userId, String role);

    boolean revoke(Long providerId, Long userId);
}
