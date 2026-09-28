package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.TankConfiguration;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankConfigurationRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.TankConfigurationPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TankConfigurationPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TankConfigurationRepositoryImpl implements TankConfigurationRepository {

    private final TankConfigurationPersistenceRepository persistenceRepository;

    public TankConfigurationRepositoryImpl(TankConfigurationPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public List<TankConfiguration> findByTankId(Long tankId) {
        return persistenceRepository.findByTankId(tankId).stream()
                .map(TankConfigurationPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public TankConfiguration save(TankConfiguration configuration) {
        var entity = TankConfigurationPersistenceAssembler.toPersistenceFromDomain(configuration);
        return TankConfigurationPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }
}
