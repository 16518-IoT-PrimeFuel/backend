package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.entities.RefillEpisodePersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefillEpisodePersistenceRepository extends JpaRepository<RefillEpisodePersistenceEntity, Long> {

    Optional<RefillEpisodePersistenceEntity> findFirstByTankIdAndStatusOrderByOpenedAtDesc(
            Long tankId, RefillEpisodeStatus status);

    Optional<RefillEpisodePersistenceEntity> findByEpisodeKey(String episodeKey);

    List<RefillEpisodePersistenceEntity> findByTankIdOrderByOpenedAtDesc(Long tankId);
}
