package com.primefuel.fulltank.platform.tracking.domain.model.valueobjects;

/**
 * A geographic position reported by the driver's phone (S16, redesign W6). Native OS GPS, no dedicated
 * hardware; {@code accuracyMeters} is optional but, when present, must be a non-negative radius.
 *
 * <p>The redesign is explicit that this position is only as trustworthy as the driver's phone — it may be
 * wrong, faked or stale. That is honest evidence, not a downgrade: this value object validates the shape of
 * the sample, it does not pretend to certify the device.
 */
public record GeoPosition(Double latitude, Double longitude, Double accuracyMeters) {

    public GeoPosition {
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be within [-90, 90]");
        }
        if (longitude == null || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be within [-180, 180]");
        }
        if (accuracyMeters != null && (!Double.isFinite(accuracyMeters) || accuracyMeters < 0.0)) {
            throw new IllegalArgumentException("Accuracy must be a non-negative number of metres");
        }
    }
}
