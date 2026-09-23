package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.MembershipDirectory;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Adapter over {@link MembershipRepository} exposing active members as {@code iam.api}. */
@Component("membershipDirectory")
public class MembershipDirectoryImpl implements MembershipDirectory {

    private final MembershipRepository membershipRepository;

    public MembershipDirectoryImpl(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public List<Long> activeMemberUserIds(Long organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return membershipRepository.findActiveByOrganizationId(organizationId).stream()
                .map(membership -> membership.getUserId())
                .toList();
    }
}
