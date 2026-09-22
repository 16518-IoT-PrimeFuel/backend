package com.primefuel.fulltank.platform.iam.domain.repositories;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {
    Optional<Membership> findById(Long id);
    List<Membership> findByUserId(Long userId);
    Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId);
    Membership save(Membership membership);
}
