package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record RegisterSiteCommand(
        Long organizationId,
        Long customerAccountId,
        String name,
        String address) {
}
