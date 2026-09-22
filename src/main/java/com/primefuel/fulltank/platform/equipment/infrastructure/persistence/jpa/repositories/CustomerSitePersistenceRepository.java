package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.CustomerSitePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerSitePersistenceRepository extends JpaRepository<CustomerSitePersistenceEntity, Long> {
    List<CustomerSitePersistenceEntity> findByCustomerAccountId(Long customerAccountId);
    List<CustomerSitePersistenceEntity> findByOrganizationId(Long organizationId);
}
