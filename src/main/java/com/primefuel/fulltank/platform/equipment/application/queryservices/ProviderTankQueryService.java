package com.primefuel.fulltank.platform.equipment.application.queryservices;

import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderTanksQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderTank;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import java.util.List;

public interface ProviderTankQueryService {
    Result<List<ProviderTank>, ApplicationError> handle(GetProviderTanksQuery query);
}
