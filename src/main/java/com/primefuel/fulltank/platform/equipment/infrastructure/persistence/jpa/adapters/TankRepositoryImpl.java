package com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.assemblers.TankPersistenceAssembler;
import com.primefuel.fulltank.platform.equipment.infrastructure.persistence.jpa.repositories.TankPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TankRepositoryImpl implements TankRepository {

    private final TankPersistenceRepository persistenceRepository;

    public TankRepositoryImpl(TankPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<Tank> findById(Long id) {
        return persistenceRepository.findById(id).map(TankPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<Tank> findByOrganizationId(Long organizationId) {
        return persistenceRepository.findByOrganizationId(organizationId).stream()
                .map(TankPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public Optional<Tank> findByLegacyEquipmentId(Long legacyEquipmentId) {
        return persistenceRepository.findByLegacyEquipmentId(legacyEquipmentId)
                .map(TankPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Tank save(Tank tank) {
        var entity = TankPersistenceAssembler.toPersistenceFromDomain(tank);
        return TankPersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }

    @Override
    public boolean existsById(Long id) {
        return persistenceRepository.existsById(id);
    }
}
