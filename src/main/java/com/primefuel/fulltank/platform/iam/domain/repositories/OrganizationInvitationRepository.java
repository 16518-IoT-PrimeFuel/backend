package com.primefuel.fulltank.platform.iam.domain.repositories;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;

import java.util.Optional;

public interface OrganizationInvitationRepository {
    Optional<OrganizationInvitation> findById(Long id);
    Optional<OrganizationInvitation> findByToken(String token);
    boolean existsByOrganizationIdAndEmailAndStatus(Long organizationId, String email, InvitationStatus status);
    OrganizationInvitation save(OrganizationInvitation invitation);
}
