package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.entities.TankerPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TankerPersistenceRepository extends JpaRepository<TankerPersistenceEntity, Long> {
    List<TankerPersistenceEntity> findByProviderId(Long providerId);
}
