package com.primefuel.fulltank.platform.equipment.application.commandservices;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateTankConfigurationCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface TankCommandService {
    Result<Tank, ApplicationError> handle(RegisterTankCommand command);
    Result<Tank, ApplicationError> handle(UpdateTankConfigurationCommand command);
}
