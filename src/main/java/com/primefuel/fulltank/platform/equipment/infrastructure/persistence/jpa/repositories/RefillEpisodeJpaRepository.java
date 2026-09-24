package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.RefillEpisodePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefillEpisodeJpaRepository extends JpaRepository<RefillEpisodePersistenceEntity, Long> {
    Optional<RefillEpisodePersistenceEntity> findByEpisodeKey(String episodeKey);

    Optional<RefillEpisodePersistenceEntity> findFirstByTankIdAndStatusIn(Long tankId, java.util.Collection<String> statuses);
}
