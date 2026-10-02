package com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform;

import com.primefuel.fulltank.platform.telemetry.domain.model.aggregates.TelemetryReading;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources.ProviderTankReadingResource;

public final class ProviderTankReadingResourceFromDomainAssembler {
    private ProviderTankReadingResourceFromDomainAssembler() {}

    public static ProviderTankReadingResource toResource(TelemetryReading r) {
        return new ProviderTankReadingResource(
                r.getId(),
                r.getTankId(),
                r.getDeviceId(),
                r.getChannel(),
                r.getSequence(),
                r.getLevel().amount(),
                r.getLevel().unit().name(),
                r.getCapturedAt(),
                r.getReceivedAt(),
                r.getQuality().name());
    }
}
