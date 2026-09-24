package com.primefuel.fulltank.platform.ordering.application.ports;

import java.util.List;
import java.util.Optional;

public interface FuelRequestStore {

    FuelRequestData save(FuelRequestData request);

    List<FuelRequestData> findAll();

    List<FuelRequestData> findByBuyerCompanyId(Long buyerCompanyId);

    List<FuelRequestData> findByProviderId(Long providerId);

    Optional<FuelRequestData> findById(Long requestId);
}
