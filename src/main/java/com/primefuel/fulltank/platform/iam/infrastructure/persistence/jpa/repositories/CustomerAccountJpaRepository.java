package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.CustomerAccountPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerAccountJpaRepository extends JpaRepository<CustomerAccountPersistenceEntity, Long> {
    Optional<CustomerAccountPersistenceEntity> findByLegacyBuyerCompanyId(Long companyId);
}
