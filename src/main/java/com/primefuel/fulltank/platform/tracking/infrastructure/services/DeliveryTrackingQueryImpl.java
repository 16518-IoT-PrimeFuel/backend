package com.primefuel.fulltank.platform.tracking.infrastructure.services;

import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Read adapter of the tracking module (S16/T16-B). It maps the projection and the raw samples onto the
 * public snapshots; the chronological ordering (device clock) is done in the persistence layer, not here.
 */
@Component("deliveryTrackingQuery")
public class DeliveryTrackingQueryImpl implements DeliveryTrackingQuery {

    private final DeliveryTrackingRepository trackingRepository;
    private final TransportEvidenceSampleRepository sampleRepository;

    public DeliveryTrackingQueryImpl(DeliveryTrackingRepository trackingRepository,
                                     TransportEvidenceSampleRepository sampleRepository) {
        this.trackingRepository = trackingRepository;
        this.sampleRepository = sampleRepository;
    }

    @Override
    public Optional<TrackingSnapshot> latest(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        return trackingRepository.findByDeliveryId(deliveryId).map(DeliveryTrackingQueryImpl::toSnapshot);
    }

    @Override
    public List<TrackingSampleSnapshot> samples(Long deliveryId) {
        if (deliveryId == null) {
            return List.of();
        }
        return sampleRepository.findByDeliveryIdOrderedByRecordedAt(deliveryId).stream()
                .map(DeliveryTrackingQueryImpl::toSampleSnapshot)
                .toList();
    }

    static TrackingSnapshot toSnapshot(DeliveryTracking tracking) {
        return new TrackingSnapshot(
                tracking.getDeliveryId(), tracking.getProviderId(), tracking.getDriverId(),
                tracking.getLastLatitude(), tracking.getLastLongitude(), tracking.getLastAccuracyMeters(),
                tracking.getLastPositionAt(), tracking.getLastPositionEvidenceId(),
                tracking.isLoaded(),
                tracking.getLastLoadMilestone() == null ? null : tracking.getLastLoadMilestone().name(),
                tracking.getLastLoadAt(), tracking.getLastLoadVolume(), tracking.getLastLoadUnit(),
                tracking.getLastLoadEvidenceId(), tracking.getVersion());
    }

    static TrackingSampleSnapshot toSampleSnapshot(TransportEvidenceSample sample) {
        return new TrackingSampleSnapshot(
                sample.getId(), sample.getKind().name(),
                sample.getLatitude(), sample.getLongitude(), sample.getAccuracyMeters(),
                sample.getMilestone(), sample.getVolume(), sample.getUnit(),
                sample.getRecordedAt(), sample.getReceivedAt(), sample.isLatestAdvanced());
    }
}
