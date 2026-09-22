package com.primefuel.fulltank.platform.fulfillment.application.commandservices;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Delivery;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.ArriveDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.AssignDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CancelDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.CompletePhysicalDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.FailDeliveryCommand;
import com.primefuel.fulltank.platform.fulfillment.domain.model.commands.StartDeliveryCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

/**
 * The physical delivery lifecycle (S14/T14-A). Separate from {@link DeliveryCommandService}: the legacy
 * command service keeps driving v1 exactly as before (compatibility), while these commands advance the
 * physical state machine and journal each transition.
 */
public interface DeliveryLifecycleService {

    Result<Delivery, ApplicationError> handle(AssignDeliveryCommand command);

    Result<Delivery, ApplicationError> handle(StartDeliveryCommand command);

    Result<Delivery, ApplicationError> handle(ArriveDeliveryCommand command);

    Result<Delivery, ApplicationError> handle(CompletePhysicalDeliveryCommand command);

    Result<Delivery, ApplicationError> handle(FailDeliveryCommand command);

    Result<Delivery, ApplicationError> handle(CancelDeliveryCommand command);
}
