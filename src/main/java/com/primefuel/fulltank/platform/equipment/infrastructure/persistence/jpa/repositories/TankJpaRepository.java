package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.entities.TankPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TankJpaRepository extends JpaRepository<TankPersistenceEntity, Long> {
    boolean existsByIdAndCustomerSiteId(Long id, Long customerSiteId);

    Optional<TankPersistenceEntity> findById(Long id);

    @Modifying
    @Query("update TankPersistenceEntity t set t.currentLevel = :level, t.lastReadingAt = :capturedAt "
            + "where t.id = :tankId and (t.lastReadingAt is null or t.lastReadingAt < :capturedAt)")
    int applyValidatedReading(@Param("tankId") Long tankId, @Param("level") double level,
                              @Param("capturedAt") java.time.Instant capturedAt);
}
