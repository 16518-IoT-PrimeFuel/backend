package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.TankerPersistenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TankerPersistenceRepository extends JpaRepository<TankerPersistenceEntity, Long> {

    List<TankerPersistenceEntity> findByProviderId(Long providerId);

    /**
     * Takes a {@code PESSIMISTIC_WRITE} lock on the tanker row (U06: lock the reserved resource, not a global
     * lock). Returns empty when the row does not exist, so the caller can answer 404.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TankerPersistenceEntity t where t.id = :id")
    Optional<TankerPersistenceEntity> lockById(@Param("id") Long id);
}
