package com.primefuel.fulltank.platform.fleet.interfaces.rest.resources;

/**
 * Eligibility answer for a single resource: {@code ELIGIBLE} / {@code BUSY} / {@code INELIGIBLE} plus a
 * short human-readable reason (S12/T12-B, U07).
 */
public record EligibilityResource(String outcome, String reason) {
}
