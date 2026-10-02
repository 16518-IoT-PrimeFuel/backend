package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.application.commandservices.*;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.*;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.*;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.inventory.api.FuelProductLookup;
import com.primefuel.fulltank.platform.replenishment.api.*;
import com.primefuel.fulltank.platform.shared.application.result.*;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.Objects;

@Service
@Transactional
public class ProviderTankCommandServiceImpl implements ProviderTankCommandService {
    private static final double DEFAULT_HYSTERESIS_PERCENT = 10.0;
    private static final double DEFAULT_TARGET_LEVEL_PERCENT = 100.0;
    private final ProviderBuyerAccess buyers;
    private final FuelProductLookup products;
    private final TankCommandService tanks;
    private final TankRepository repository;
    private final DeviceBindingRepository bindings;
    private final DeviceBindingCommandService devices;
    private final TankRefillConfiguration policies;
    private final TankRefillLookup policyLookup;

    public ProviderTankCommandServiceImpl(
            ProviderBuyerAccess buyers,
            FuelProductLookup products,
            TankCommandService tanks,
            TankRepository repository,
            DeviceBindingRepository bindings,
            DeviceBindingCommandService devices,
            TankRefillConfiguration policies,
            TankRefillLookup policyLookup) {
        this.buyers = buyers;
        this.products = products;
        this.tanks = tanks;
        this.repository = repository;
        this.bindings = bindings;
        this.devices = devices;
        this.policies = policies;
        this.policyLookup = policyLookup;
    }

    public Result<Long, ApplicationError> handle(RegisterProviderTankCommand c) {
        var organization =
                buyers.linkedBuyerOrganization(c.providerId(), c.buyerCompanyId()).orElse(null);
        if (organization == null)
            return Result.failure(
                    ApplicationError.notFound("BuyerCompany", String.valueOf(c.buyerCompanyId())));
        var product =
                products.findById(c.fuelProductId())
                        .filter(p -> c.providerId().equals(p.providerId()) && p.active())
                        .orElse(null);
        if (product == null)
            return Result.failure(
                    ApplicationError.notFound("FuelProduct", String.valueOf(c.fuelProductId())));
        var conflict = bindings.findOpenByDeviceId(c.deviceId()).stream().findFirst();
        if (conflict.isPresent())
            return Result.failure(
                    ApplicationError.conflict(
                            "DeviceBinding",
                            duplicateReason(c.providerId(), conflict.get().getTankId())));
        var created =
                tanks.handle(
                        new RegisterTankCommand(
                                organization,
                                c.customerAccountId(),
                                c.siteId(),
                                c.name(),
                                product.fuelType(),
                                c.capacity(),
                                c.unit(),
                                c.initialLevel(),
                                null));
        if (created instanceof Result.Failure<?, ?> failed)
            return rollback((ApplicationError) failed.error());
        var tank = created.getOrElse(null);
        var bound =
                devices.handle(
                        new BindDeviceCommand(
                                organization,
                                c.deviceId(),
                                c.channel(),
                                tank.getId(),
                                Instant.now()));
        if (bound instanceof Result.Failure<?, ?> failed)
            return rollback((ApplicationError) failed.error());
        var configured =
                policies.configure(
                        tank.getId(),
                        organization,
                        c.lowLevelPercent(),
                        DEFAULT_HYSTERESIS_PERCENT,
                        DEFAULT_TARGET_LEVEL_PERCENT,
                        c.providerId(),
                        c.fuelProductId(),
                        c.autoGenerateEnabled());
        if (configured instanceof Result.Failure<?, ?> failed)
            return rollback((ApplicationError) failed.error());
        return Result.success(tank.getId());
    }

    private String duplicateReason(Long providerId, Long tankId) {
        return buyers.canReadTank(providerId, tankId)
                ? "Device already linked to tank " + tankId
                : "Device already linked; existing tenant details are private";
    }

    private static Result<Long, ApplicationError> rollback(ApplicationError error) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return Result.failure(error);
    }
}
