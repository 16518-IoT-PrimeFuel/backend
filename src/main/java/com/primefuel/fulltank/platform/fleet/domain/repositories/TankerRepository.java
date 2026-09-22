package com.primefuel.fulltank.platform.fleet.domain.repositories;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;

import java.util.List;
import java.util.Optional;

public interface TankerRepository {
    Optional<Tanker> findById(Long id);
    List<Tanker> findByProviderId(Long providerId);
    Tanker save(Tanker tanker);
}
