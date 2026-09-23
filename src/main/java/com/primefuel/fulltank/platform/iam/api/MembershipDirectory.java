package com.primefuel.fulltank.platform.iam.api;

import java.util.List;

/**
 * Public read seam over organization memberships (S20/T20-A). Other modules resolve the recipients of a
 * notification from their <em>scope</em> (an organization id) through this interface, never by querying the
 * membership repository. Only active (non-revoked) members are returned, which is what makes a revoked user
 * stop receiving new fanout.
 */
public interface MembershipDirectory {

    /** User ids of the active members of {@code organizationId}, in a stable order. Empty when unknown. */
    List<Long> activeMemberUserIds(Long organizationId);
}
