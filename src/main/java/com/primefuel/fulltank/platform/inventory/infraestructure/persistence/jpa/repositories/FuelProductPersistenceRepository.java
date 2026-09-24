package com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.inventory.infrastructure.persistence.jpa.entities.FuelProductPersistenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FuelProductPersistenceRepository extends JpaRepository<FuelProductPersistenceEntity, Long> {
    List<FuelProductPersistenceEntity> findByProviderId(Long providerId);

    @Modifying
    @Query("update FuelProductPersistenceEntity p set p.availableStock = p.availableStock - :quantity "
            + "where p.id = :productId and p.availableStock >= :quantity and p.active = true")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") Double quantity);
}
