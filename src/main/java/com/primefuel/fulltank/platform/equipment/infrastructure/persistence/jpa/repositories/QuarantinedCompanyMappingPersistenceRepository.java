package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.QuarantinedCompanyMappingPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuarantinedCompanyMappingPersistenceRepository
        extends JpaRepository<QuarantinedCompanyMappingPersistenceEntity, Long> {

    Optional<QuarantinedCompanyMappingPersistenceEntity> findByLegacyCompanyId(Long legacyCompanyId);
}
