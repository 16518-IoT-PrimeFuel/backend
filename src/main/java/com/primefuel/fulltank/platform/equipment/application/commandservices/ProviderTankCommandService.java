package com.primefuel.fulltank.platform.equipment.application.commandservices;

import com.primefuel.fulltank.platform.equipment.domain.model.commands.*;
import com.primefuel.fulltank.platform.shared.application.result.*;

public interface ProviderTankCommandService {
    Result<Long, ApplicationError> handle(RegisterProviderTankCommand command);

}
