package com.primefuel.fulltank.platform.iam.domain.model.commands;

import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;

public record CreateOrganizationCommand(
        String name,
        String ruc,
        OrganizationType type) {
}
