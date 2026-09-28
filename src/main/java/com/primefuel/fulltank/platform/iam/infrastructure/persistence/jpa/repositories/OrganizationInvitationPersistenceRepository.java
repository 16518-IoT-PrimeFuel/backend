package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.OrganizationInvitationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationInvitationPersistenceRepository
        extends JpaRepository<OrganizationInvitationPersistenceEntity, Long> {

    Optional<OrganizationInvitationPersistenceEntity> findByToken(String token);

    boolean existsByOrganizationIdAndEmailAndStatus(Long organizationId, String email, InvitationStatus status);
}
