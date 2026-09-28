package com.primefuel.fulltank.platform.fleet.api;

import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.RegisterTankerCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateDriverCommand;
import com.primefuel.fulltank.platform.fleet.domain.model.commands.UpdateTankerCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

/**
 * Write seam over the fleet catalog: register/update and the explicit enable/disable commands. Deleting a
 * resource is not modelled — it is disabled ({@link #deactivateDriver}, {@link #deactivateTanker}) so the
 * history is preserved (S12).
 */
public interface FleetRegistry {

    Result<FleetCatalog.DriverSnapshot, ApplicationError> registerDriver(RegisterDriverCommand command);

    Result<FleetCatalog.DriverSnapshot, ApplicationError> updateDriver(UpdateDriverCommand command);

    Result<FleetCatalog.DriverSnapshot, ApplicationError> deactivateDriver(Long driverId);

    Result<FleetCatalog.DriverSnapshot, ApplicationError> activateDriver(Long driverId);

    Result<FleetCatalog.TankerSnapshot, ApplicationError> registerTanker(RegisterTankerCommand command);

    Result<FleetCatalog.TankerSnapshot, ApplicationError> updateTanker(UpdateTankerCommand command);

    Result<FleetCatalog.TankerSnapshot, ApplicationError> deactivateTanker(Long tankerId);

    Result<FleetCatalog.TankerSnapshot, ApplicationError> activateTanker(Long tankerId);
}
