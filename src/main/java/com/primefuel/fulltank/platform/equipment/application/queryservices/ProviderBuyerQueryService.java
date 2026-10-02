package com.primefuel.fulltank.platform.equipment.application.queryservices;

import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.LookupProviderBuyerCompanyQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerIdentity;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerCompany;

import java.util.List;

public interface ProviderBuyerQueryService {
    Result<ProviderBuyerIdentity, ApplicationError> handle(LookupProviderBuyerCompanyQuery query);

    List<ProviderBuyerCompany> handle(GetProviderBuyerCompaniesQuery query);
}
