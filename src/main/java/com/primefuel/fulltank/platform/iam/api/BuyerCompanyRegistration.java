package com.primefuel.fulltank.platform.iam.api;


import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface BuyerCompanyRegistration {
    Result<BuyerCompanyDirectory.BuyerSnapshot, ApplicationError> register(
            Registration registration);

    record Registration(
            Long buyerCompanyId,
            String name,
            String ruc,
            String sector,
            String address,
            String contactEmail,
            String phone) {}
}
