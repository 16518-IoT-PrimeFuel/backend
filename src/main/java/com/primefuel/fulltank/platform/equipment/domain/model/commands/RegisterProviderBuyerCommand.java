package com.primefuel.fulltank.platform.equipment.domain.model.commands;

public record RegisterProviderBuyerCommand(
        Long providerId,
        Long buyerCompanyId,
        String name,
        String ruc,
        String sector,
        String address,
        String contactEmail,
        String phone,
        String siteName) {}
