package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.MembershipPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipPersistenceRepository extends JpaRepository<MembershipPersistenceEntity, Long> {
    List<MembershipPersistenceEntity> findByUserId(Long userId);
    Optional<MembershipPersistenceEntity> findByOrganizationIdAndUserId(Long organizationId, Long userId);
    List<MembershipPersistenceEntity> findByOrganizationIdAndActiveTrueOrderByUserIdAsc(Long organizationId);
}
