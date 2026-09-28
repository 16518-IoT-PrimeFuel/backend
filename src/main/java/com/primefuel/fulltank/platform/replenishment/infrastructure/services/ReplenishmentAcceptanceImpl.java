package com.primefuel.fulltank.platform.replenishment.infrastructure.services;

import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentAcceptance;
import com.primefuel.fulltank.platform.replenishment.application.commandservices.ReplenishmentCommandService;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Component;

/** Adapter over {@link ReplenishmentCommandService} so other modules consume acceptance through the seam. */
@Component("replenishmentAcceptance")
public class ReplenishmentAcceptanceImpl implements ReplenishmentAcceptance {

    private final ReplenishmentCommandService commandService;

    public ReplenishmentAcceptanceImpl(ReplenishmentCommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public Result<Boolean, ApplicationError> consume(Long requestId) {
        return commandService.handle(new ConsumeReplenishmentAcceptanceCommand(requestId));
    }
}
