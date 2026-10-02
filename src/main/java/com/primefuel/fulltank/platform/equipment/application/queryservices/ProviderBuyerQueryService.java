package com.primefuel.fulltank.platform.equipment.application.queryservices;

import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerCompany;

import java.util.List;

public interface ProviderBuyerQueryService {
    List<ProviderBuyerCompany> handle(GetProviderBuyerCompaniesQuery query);
}
