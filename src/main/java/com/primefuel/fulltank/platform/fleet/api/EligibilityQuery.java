package com.primefuel.fulltank.platform.fleet.api;

import java.util.List;
import java.util.Optional;

/**
 * Eligibility seam over the fleet catalog (S12/T12-B). It only answers "may this resource be suggested
 * for an assignment to this tenant?" — no routing, no ranking (explicitly out of scope for S12).
 *
 * <p>The evaluation is deliberately three-valued, not boolean. Following the product decision (U07,
 * 2026-09-22):
 * <ul>
 *   <li>{@link Outcome#ELIGIBLE} — allowed status ({@code AVAILABLE}) <em>and</em> {@code active=true};</li>
 *   <li>{@link Outcome#BUSY} — {@code ASSIGNED}/{@code IN_ROUTE}: the resource exists and is healthy, but
 *       it is currently occupied, so it is not suggested. This is <em>not</em> the same as ineligible: a
 *       later query may find it eligible again without any administrative action;</li>
 *   <li>{@link Outcome#INELIGIBLE} — disabled ({@code active=false}) or a non-usable status
 *       ({@code SUSPENDED}/{@code MAINTENANCE}/{@code INACTIVE}).</li>
 * </ul>
 *
 * <p>There is no validity/expiry date (U07): eligibility is status + lifecycle flag + tenant only.
 * Every method takes the caller's tenant and never exposes a resource from another tenant — an
 * assessment for a foreign id is simply empty.
 */
public interface EligibilityQuery {

    enum Outcome {
        ELIGIBLE,
        BUSY,
        INELIGIBLE
    }

    Optional<DriverAssessment> assessDriver(Long providerId, Long driverId);

    List<FleetCatalog.DriverSnapshot> eligibleDrivers(Long providerId);

    Optional<TankerAssessment> assessTanker(Long providerId, Long tankerId);

    List<FleetCatalog.TankerSnapshot> eligibleTankers(Long providerId);

    record DriverAssessment(Long driverId, Long providerId, Outcome outcome, String reason) {
    }

    record TankerAssessment(Long tankerId, Long providerId, Outcome outcome, String reason) {
    }
}
