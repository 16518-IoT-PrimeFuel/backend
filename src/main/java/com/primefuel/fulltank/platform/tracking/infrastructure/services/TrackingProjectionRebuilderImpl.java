package com.primefuel.fulltank.platform.tracking.infrastructure.services;

import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Unit;
import com.primefuel.fulltank.platform.shared.domain.model.valueobjects.Volume;
import com.primefuel.fulltank.platform.tracking.api.DeliveryTrackingQuery;
import com.primefuel.fulltank.platform.tracking.api.TrackingProjectionRebuilder;
import com.primefuel.fulltank.platform.tracking.domain.model.aggregates.DeliveryTracking;
import com.primefuel.fulltank.platform.tracking.domain.model.entities.TransportEvidenceSample;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.GeoPosition;
import com.primefuel.fulltank.platform.tracking.domain.model.valueobjects.LoadMilestone;
import com.primefuel.fulltank.platform.tracking.domain.repositories.DeliveryTrackingRepository;
import com.primefuel.fulltank.platform.tracking.domain.repositories.TransportEvidenceSampleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Rebuilds the tracking projection from the raw evidence (S16/T16-B). It resets the projection and replays
 * the samples in device-clock ({@code recordedAt}) order through the <em>same</em> aggregate the live
 * recorder uses, then persists the result. Because the aggregate is deterministic, replaying the same
 * evidence always produces the same projection — out-of-order arrival (jitter) does not change the outcome,
 * only the moment a sample becomes visible.
 *
 * <p>The driver is taken from the most recently <em>received</em> sample, which is exactly what the live
 * recorder keeps (each accepted sample refreshes the assignment), so a rebuild matches the live projection
 * field for field.
 */
@Component("trackingProjectionRebuilder")
public class TrackingProjectionRebuilderImpl implements TrackingProjectionRebuilder {

    private final DeliveryTrackingRepository trackingRepository;
    private final TransportEvidenceSampleRepository sampleRepository;

    public TrackingProjectionRebuilderImpl(DeliveryTrackingRepository trackingRepository,
                                           TransportEvidenceSampleRepository sampleRepository) {
        this.trackingRepository = trackingRepository;
        this.sampleRepository = sampleRepository;
    }

    @Override
    @Transactional
    public Optional<DeliveryTrackingQuery.TrackingSnapshot> rebuild(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        var samples = sampleRepository.findByDeliveryIdOrderedByRecordedAt(deliveryId);
        if (samples.isEmpty()) {
            return Optional.empty();
        }

        var existing = trackingRepository.findByDeliveryId(deliveryId);
        DeliveryTracking tracking = existing.orElseGet(
                () -> new DeliveryTracking(deliveryId, samples.get(0).getProviderId(), null));
        tracking.reset();
        for (var sample : samples) {
            replay(tracking, sample);
        }
        tracking.setDriverId(latestReceivedDriverId(samples));

        var saved = trackingRepository.save(tracking);
        return Optional.of(DeliveryTrackingQueryImpl.toSnapshot(saved));
    }

    private static void replay(DeliveryTracking tracking, TransportEvidenceSample sample) {
        switch (sample.getKind()) {
            case POSITION -> {
                boolean advanced = tracking.recordPosition(
                        new GeoPosition(sample.getLatitude(), sample.getLongitude(), sample.getAccuracyMeters()),
                        sample.getRecordedAt());
                if (advanced) {
                    tracking.linkPositionEvidence(sample.getId());
                }
            }
            case LOAD -> {
                var milestone = LoadMilestone.fromCode(sample.getMilestone());
                var volume = sample.getVolume() == null
                        ? null
                        : Volume.of(sample.getVolume(), Unit.fromCode(sample.getUnit()));
                boolean advanced;
                try {
                    advanced = tracking.recordLoad(milestone, volume, sample.getRecordedAt());
                } catch (IllegalStateException sequenceViolation) {
                    // Raw evidence can carry a chronologically impossible LOAD sequence (e.g. a late
                    // UNLOADED whose recordedAt precedes every LOADED seen so far). The live recorder never
                    // hits this because a late sample is filtered out before the sequence check runs; a
                    // rebuild replays strictly by recordedAt, so this sample looks like it should advance.
                    // Treat it the same way a late sample is treated: keep the raw evidence, don't move the
                    // projection, never fail the rebuild over inconsistent raw data.
                    advanced = false;
                }
                if (advanced) {
                    tracking.linkLoadEvidence(sample.getId());
                }
            }
        }
    }

    /** The driver of the last-received sample, matching the live recorder's assignment refresh. */
    private static Long latestReceivedDriverId(List<TransportEvidenceSample> samples) {
        TransportEvidenceSample latest = null;
        for (var sample : samples) {
            if (latest == null
                    || sample.getReceivedAt().isAfter(latest.getReceivedAt())
                    || (sample.getReceivedAt().equals(latest.getReceivedAt())
                        && sample.getId() > latest.getId())) {
                latest = sample;
            }
        }
        return latest == null ? null : latest.getDriverId();
    }
}
