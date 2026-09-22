package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.ReplenishmentRequestPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplenishmentRequestPersistenceRepository
        extends JpaRepository<ReplenishmentRequestPersistenceEntity, Long> {

    List<ReplenishmentRequestPersistenceEntity> findByOrganizationId(Long organizationId);
    Optional<ReplenishmentRequestPersistenceEntity> findByEpisodeKey(String episodeKey);
}
