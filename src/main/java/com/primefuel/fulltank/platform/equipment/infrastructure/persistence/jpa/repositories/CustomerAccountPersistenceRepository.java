package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.CustomerAccountPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAccountPersistenceRepository extends JpaRepository<CustomerAccountPersistenceEntity, Long> {
    List<CustomerAccountPersistenceEntity> findByOrganizationId(Long organizationId);
    Optional<CustomerAccountPersistenceEntity> findByLegacyCompanyId(Long legacyCompanyId);
    Optional<CustomerAccountPersistenceEntity> findByOrganizationIdAndRuc(Long organizationId, String ruc);
}
