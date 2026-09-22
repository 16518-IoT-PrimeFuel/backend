package com.primefuel.fulltank.platform.replenishment.application.commandservices;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AcceptReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.AttachReplenishmentOrderCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CancelReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConsumeReplenishmentAcceptanceCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.RejectReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface ReplenishmentCommandService {
    Result<ReplenishmentRequest, ApplicationError> handle(CreateReplenishmentRequestCommand command);
    Result<ReplenishmentRequest, ApplicationError> handle(AcceptReplenishmentRequestCommand command);
    Result<ReplenishmentRequest, ApplicationError> handle(RejectReplenishmentRequestCommand command);
    Result<ReplenishmentRequest, ApplicationError> handle(CancelReplenishmentRequestCommand command);
    Result<Boolean, ApplicationError> handle(ConsumeReplenishmentAcceptanceCommand command);
    Result<ReplenishmentRequest, ApplicationError> handle(AttachReplenishmentOrderCommand command);
}
