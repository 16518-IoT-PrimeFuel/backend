package com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.application.queryservices.MembershipQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByUserIdQuery;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("membershipAccess")
public class MembershipAccessImpl implements MembershipAccess {

    private final MembershipQueryService membershipQueryService;

    public MembershipAccessImpl(MembershipQueryService membershipQueryService) {
        this.membershipQueryService = membershipQueryService;
    }

    @Override
    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl principal) {
            return Optional.ofNullable(principal.getUserId());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> currentOrganizationId() {
        return currentUserId().flatMap(userId -> activeMemberships(userId).stream()
                .map(Membership::getOrganizationId)
                .findFirst());
    }

    @Override
    public boolean belongsToOrganization(Long organizationId) {
        if (organizationId == null) {
            return false;
        }
        return currentUserId()
                .map(userId -> activeMemberships(userId).stream()
                        .anyMatch(membership -> organizationId.equals(membership.getOrganizationId())))
                .orElse(false);
    }

    private java.util.List<Membership> activeMemberships(Long userId) {
        return membershipQueryService.handle(new GetMembershipsByUserIdQuery(userId)).stream()
                .filter(Membership::isActive)
                .toList();
    }
}
