package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.assemblers.MembershipPersistenceAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.MembershipPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MembershipRepositoryImpl implements MembershipRepository {

    private final MembershipPersistenceRepository membershipPersistenceRepository;

    public MembershipRepositoryImpl(MembershipPersistenceRepository membershipPersistenceRepository) {
        this.membershipPersistenceRepository = membershipPersistenceRepository;
    }

    @Override
    public Optional<Membership> findById(Long id) {
        return membershipPersistenceRepository.findById(id)
                .map(MembershipPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Membership> findByUserId(Long userId) {
        return membershipPersistenceRepository.findByUserId(userId).stream()
                .map(MembershipPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public Optional<Membership> findByOrganizationIdAndUserId(Long organizationId, Long userId) {
        return membershipPersistenceRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(MembershipPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Membership> findActiveByOrganizationId(Long organizationId) {
        return membershipPersistenceRepository.findByOrganizationIdAndActiveTrueOrderByUserIdAsc(organizationId)
                .stream()
                .map(MembershipPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public Membership save(Membership membership) {
        var entity = MembershipPersistenceAssembler.toPersistenceFromDomain(membership);
        return MembershipPersistenceAssembler.toDomainFromPersistence(
                membershipPersistenceRepository.save(entity));
    }
}
