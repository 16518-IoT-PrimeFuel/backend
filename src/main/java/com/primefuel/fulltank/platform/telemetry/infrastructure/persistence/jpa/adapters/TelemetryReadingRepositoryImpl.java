package com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.domain.model.valueobjects.ReadingQuality;
import com.primefuel.fulltank.platform.telemetry.domain.repositories.TelemetryReadingRepository;
import com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.assemblers.TelemetryReadingPersistenceAssembler;
import com.primefuel.fulltank.platform.telemetry.infrastructure.persistence.jpa.repositories.TelemetryReadingPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TelemetryReadingRepositoryImpl implements TelemetryReadingRepository {

    private final TelemetryReadingPersistenceRepository persistenceRepository;

    public TelemetryReadingRepositoryImpl(TelemetryReadingPersistenceRepository persistenceRepository) {
        this.persistenceRepository = persistenceRepository;
    }

    @Override
    public Optional<TelemetryReading> findByDeviceChannelAndSequence(String deviceId, String channel, long sequence) {
        return persistenceRepository.findByDeviceIdAndChannelAndSequence(deviceId, channel, sequence)
                .map(TelemetryReadingPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<TelemetryReading> findByDeviceAndChannelOrderByCapturedAtAsc(String deviceId, String channel) {
        return persistenceRepository.findByDeviceIdAndChannelOrderByCapturedAtAsc(deviceId, channel).stream()
                .map(TelemetryReadingPersistenceAssembler::toDomainFromPersistence).toList();
    }

    @Override
    public long countByQuality(ReadingQuality quality) {
        return persistenceRepository.countByQuality(quality);
    }

    @Override
    public TelemetryReading save(TelemetryReading reading) {
        var entity = TelemetryReadingPersistenceAssembler.toPersistenceFromDomain(reading);
        return TelemetryReadingPersistenceAssembler.toDomainFromPersistence(
                persistenceRepository.saveAndFlush(entity));
    }
}
