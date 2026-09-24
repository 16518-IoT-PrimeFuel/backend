package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.CustomerSitePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSiteJpaRepository extends JpaRepository<CustomerSitePersistenceEntity, Long> {
}
