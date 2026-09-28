package com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects;

/** Outcome of authenticating a device channel for a given instant. */
public enum DeviceAuthenticationOutcome {
    AUTHENTICATED,
    UNKNOWN_CREDENTIAL,
    REVOKED_CREDENTIAL,
    NO_ACTIVE_BINDING
}
