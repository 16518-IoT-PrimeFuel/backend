package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.commands.SignUpCommand;
import com.primefuel.fulltank.platform.iam.domain.model.entities.Role;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.SignUpResource;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateBuyerCompanyCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateProviderCompanyCommand;

import java.util.List;

public final class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {
    }

    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        var roles = resource.roles() != null
                ? resource.roles().stream().map(Role::toRoleFromName).toList()
                : List.<Role>of();
        var buyer = resource.buyerCompany() == null ? null : new CreateBuyerCompanyCommand(
                resource.buyerCompany().name(), resource.buyerCompany().ruc(), resource.buyerCompany().sector(),
                resource.buyerCompany().address(), resource.buyerCompany().contactEmail(),
                resource.buyerCompany().phone());
        var provider = resource.providerCompany() == null ? null : new CreateProviderCompanyCommand(
                resource.providerCompany().name(), resource.providerCompany().ruc(), 0.0,
                resource.providerCompany().address(), resource.providerCompany().phone(),
                resource.providerCompany().fuelTypesOffered(), resource.providerCompany().description());
        return new SignUpCommand(resource.username(), resource.password(), roles, buyer, provider);
    }
}
