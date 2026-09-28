package com.primefuel.fulltank.platform.equipment.application.queryservices;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTankByIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetTanksByOrganizationQuery;

import java.util.List;
import java.util.Optional;

public interface TankQueryService {
    Optional<Tank> handle(GetTankByIdQuery query);
    List<Tank> handle(GetTanksByOrganizationQuery query);
}
