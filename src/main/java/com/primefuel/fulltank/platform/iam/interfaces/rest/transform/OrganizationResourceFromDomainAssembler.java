package com.primefuel.fulltank.platform.iam.interfaces.rest.transform;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.OrganizationResource;

public final class OrganizationResourceFromDomainAssembler {

    private OrganizationResourceFromDomainAssembler() {
    }

    public static OrganizationResource toResourceFromDomain(Organization organization, MembershipRole role) {
        return new OrganizationResource(
                organization.getId(),
                organization.getName(),
                organization.getType() == null ? null : organization.getType().name(),
                role == null ? null : role.name());
    }
}
