package com.primefuel.fulltank.platform.iam.api;

import java.util.Optional;

public interface MembershipAccess {

    Optional<Long> currentUserId();

    Optional<Long> currentOrganizationId();

    boolean belongsToOrganization(Long organizationId);

    /** OWNER o ADMIN activo en la organización: puede invitar y revocar. */
    boolean canManageOrganization(Long organizationId);
}
