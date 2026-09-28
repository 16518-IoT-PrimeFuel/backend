package com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.repositories;

import com.primefuel.fulltank.platform.supply.infrastructure.persistence.jpa.entities.SupplyStockLockPersistenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplyStockLockPersistenceRepository extends JpaRepository<SupplyStockLockPersistenceEntity, Long> {

    Optional<SupplyStockLockPersistenceEntity> findByProviderIdAndFuelProductId(Long providerId, Long fuelProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SupplyStockLockPersistenceEntity l "
            + "where l.providerId = :providerId and l.fuelProductId = :fuelProductId")
    Optional<SupplyStockLockPersistenceEntity> lockByProduct(@Param("providerId") Long providerId,
                                                             @Param("fuelProductId") Long fuelProductId);
}
