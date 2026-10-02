package com.primefuel.fulltank.platform.equipment.application.commandservices;

import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterProviderBuyerCommand;
import com.primefuel.fulltank.platform.shared.application.result.*;

public interface ProviderBuyerCommandService {
    Result<Long, ApplicationError> handle(RegisterProviderBuyerCommand command);
}
