package com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillEpisode;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillEpisodeStatus;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.RefillEpisodeRepository;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.assemblers.RefillEpisodePersistenceAssembler;
import com.primefuel.fulltank.platform.replenishment.infrastructure.persistence.jpa.repositories.RefillEpisodePersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RefillEpisodeRepositoryImpl implements RefillEpisodeRepository {

    private final RefillEpisodePersistenceRepository persistenceRepository;

    public RefillEpisodeRepositoryImpl(RefillEpisodePersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<RefillEpisode> findOpenByTankId(Long tankId) {
        return persistenceRepository.findFirstByTankIdAndStatusOrderByOpenedAtDesc(tankId, RefillEpisodeStatus.OPEN)
                .map(RefillEpisodePersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public Optional<RefillEpisode> findByEpisodeKey(String episodeKey) {
        return persistenceRepository.findByEpisodeKey(episodeKey)
                .map(RefillEpisodePersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<RefillEpisode> findByTankId(Long tankId) {
        return persistenceRepository.findByTankIdOrderByOpenedAtDesc(tankId).stream()
                .map(RefillEpisodePersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public RefillEpisode save(RefillEpisode episode) {
        var entity = RefillEpisodePersistenceAssembler.toPersistenceFromDomain(episode);
        return RefillEpisodePersistenceAssembler.toDomainFromPersistence(persistenceRepository.save(entity));
    }

    @Override
    public RefillEpisode saveAndFlush(RefillEpisode episode) {
        var entity = RefillEpisodePersistenceAssembler.toPersistenceFromDomain(episode);
        return RefillEpisodePersistenceAssembler.toDomainFromPersistence(persistenceRepository.saveAndFlush(entity));
    }
}
