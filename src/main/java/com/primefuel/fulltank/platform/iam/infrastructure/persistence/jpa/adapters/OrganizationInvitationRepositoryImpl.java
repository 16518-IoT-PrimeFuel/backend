package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.OrganizationInvitation;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.InvitationStatus;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationInvitationRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers.OrganizationInvitationPersistenceAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.OrganizationInvitationPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationInvitationRepositoryImpl implements OrganizationInvitationRepository {

    private final OrganizationInvitationPersistenceRepository invitationPersistenceRepository;

    public OrganizationInvitationRepositoryImpl(
            OrganizationInvitationPersistenceRepository invitationPersistenceRepository) {
        this.invitationPersistenceRepository = invitationPersistenceRepository;
    }

    @Override
    public Optional<OrganizationInvitation> findById(Long id) {
        return invitationPersistenceRepository.findById(id)
                .map(OrganizationInvitationPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<OrganizationInvitation> findByToken(String token) {
        return invitationPersistenceRepository.findByToken(token)
                .map(OrganizationInvitationPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public boolean existsByOrganizationIdAndEmailAndStatus(Long organizationId, String email, InvitationStatus status) {
        return invitationPersistenceRepository.existsByOrganizationIdAndEmailAndStatus(organizationId, email, status);
    }

    @Override
    public OrganizationInvitation save(OrganizationInvitation invitation) {
        var entity = OrganizationInvitationPersistenceAssembler.toPersistenceFromDomain(invitation);
        return OrganizationInvitationPersistenceAssembler.toDomainFromPersistence(
                invitationPersistenceRepository.save(entity));
    }
}
