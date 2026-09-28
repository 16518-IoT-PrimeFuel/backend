package com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.fleet.domain.model.aggregates.Tanker;
import com.primefuel.fulltank.platform.fleet.domain.repositories.TankerRepository;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.assemblers.TankerPersistenceAssembler;
import com.primefuel.fulltank.platform.fleet.infrastructure.persistence.jpa.repositories.TankerPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TankerRepositoryImpl implements TankerRepository {

    private final TankerPersistenceRepository persistenceRepository;

    public TankerRepositoryImpl(TankerPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<Tanker> findById(Long id) {
        return persistenceRepository.findById(id).map(TankerPersistenceAssembler::toDomain);
    }

    @Override
    public List<Tanker> findByProviderId(Long providerId) {
        return persistenceRepository.findByProviderId(providerId).stream()
                .map(TankerPersistenceAssembler::toDomain).toList();
    }

    @Override
    public Tanker save(Tanker tanker) {
        return TankerPersistenceAssembler.toDomain(
                persistenceRepository.save(TankerPersistenceAssembler.toPersistence(tanker)));
    }
}
