package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.OrganizationPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationPersistenceRepository extends JpaRepository<OrganizationPersistenceEntity, Long> {
    Optional<OrganizationPersistenceEntity> findByRuc(String ruc);
}
