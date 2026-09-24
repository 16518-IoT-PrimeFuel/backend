package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.MembershipPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMembershipJpaRepository extends JpaRepository<MembershipPersistenceEntity, Long> {
    java.util.Optional<MembershipPersistenceEntity> findByUserIdAndOrganizationId(Long userId, Long organizationId);
    @Query(value = "select count(*) > 0 from memberships m join organizations o on o.id = m.organization_id "
            + "where m.user_id = :userId and m.status = 'ACTIVE' and o.status = 'ACTIVE'", nativeQuery = true)
    boolean hasActiveMemberships(@Param("userId") Long userId);

    @Query(value = "select count(*) > 0 from memberships m join organizations o on o.id = m.organization_id "
            + "where m.user_id = :userId and m.status = 'ACTIVE' and o.status = 'ACTIVE' "
            + "and o.legacy_buyer_company_id = :companyId", nativeQuery = true)
    boolean ownsBuyerCompany(@Param("userId") Long userId, @Param("companyId") Long companyId);

    @Query(value = "select count(*) > 0 from memberships m join organizations o on o.id = m.organization_id "
            + "where m.user_id = :userId and m.status = 'ACTIVE' and o.status = 'ACTIVE' "
            + "and o.legacy_provider_company_id = :providerId", nativeQuery = true)
    boolean ownsProviderCompany(@Param("userId") Long userId, @Param("providerId") Long providerId);
}
