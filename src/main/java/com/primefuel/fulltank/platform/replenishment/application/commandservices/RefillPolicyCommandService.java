package com.primefuel.fulltank.platform.replenishment.application.commandservices;

import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.RefillPolicy;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.ConfigureRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.EvaluateRefillPolicyCommand;
import com.primefuel.fulltank.platform.replenishment.domain.model.valueobjects.RefillDecision;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface RefillPolicyCommandService {

    Result<RefillPolicy, ApplicationError> handle(ConfigureRefillPolicyCommand command);

    Result<RefillDecision, ApplicationError> handle(EvaluateRefillPolicyCommand command);
}
