package com.primefuel.fulltank.platform.telemetry.interfaces.rest.transform;

import com.primefuel.fulltank.platform.telemetry.application.internal.commandservices.TelemetryIngestServiceImpl;
import com.primefuel.fulltank.platform.telemetry.interfaces.rest.resources.ReadingAckResource;

public final class ReadingAckFromResultAssembler {

    private ReadingAckFromResultAssembler() {
    }

    public static ReadingAckResource toResourceFromResult(TelemetryIngestServiceImpl.IngestResult result) {
        return new ReadingAckResource(result.readingId(), result.quality(), result.tankId(), result.duplicate());
    }
}
