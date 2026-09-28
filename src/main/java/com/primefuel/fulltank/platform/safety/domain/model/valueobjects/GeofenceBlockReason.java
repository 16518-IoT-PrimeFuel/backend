package com.primefuel.fulltank.platform.safety.domain.model.valueobjects;

/** Why a geofence decision blocked the (future) valve command (S17/U09). */
public enum GeofenceBlockReason {
    /** The delivery has no geofence policy configured, so there is nothing to authorize against. */
    NO_POLICY,
    /** There is no trusted position for the delivery yet. */
    NO_POSITION,
    /** The position claims to be from the future beyond the tolerated clock skew, so it cannot be trusted. */
    FUTURE_TIMESTAMP,
    /** The last trusted position is older than the freshness window. */
    STALE,
    /** The reported accuracy is unknown or worse than the allowed uncertainty. */
    INACCURATE,
    /** The uncertainty circle reaches or crosses the geofence border (outside or on the border). */
    OUTSIDE
}
