package com.primefuel.fulltank.platform.telemetry.application.commandservices;

import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.telemetry.domain.model.commands.IngestTelemetryCommand;

public interface TelemetryIngestService {

    Result<IngestResult, ApplicationError> handle(IngestTelemetryCommand command);

    /**
     * @param duplicate true when the same device/channel/sequence was already stored; nothing was written
     *                  in that case, which is what makes ingestion replay-safe.
     */
    record IngestResult(Long readingId, String quality, Long tankId, boolean duplicate) {
    }
}
