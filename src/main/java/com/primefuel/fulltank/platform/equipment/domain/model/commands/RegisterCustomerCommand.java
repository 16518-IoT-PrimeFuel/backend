package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record RegisterCustomerCommand(
        Long organizationId,
        String name,
        String ruc,
        String address,
        String contactEmail,
        String phone,
        Long legacyCompanyId) {
}
