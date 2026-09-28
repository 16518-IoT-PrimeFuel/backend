package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.TankConfiguration;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateTankConfigurationCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankConfigurationRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TankCommandServiceImpl implements TankCommandService {

    private final TankRepository tankRepository;
    private final TankConfigurationRepository tankConfigurationRepository;
    private final CustomerDirectory customerDirectory;
    private final CustomerSiteRepository customerSiteRepository;

    public TankCommandServiceImpl(TankRepository tankRepository,
                                  TankConfigurationRepository tankConfigurationRepository,
                                  CustomerDirectory customerDirectory,
                                  CustomerSiteRepository customerSiteRepository) {
        this.tankRepository = tankRepository;
        this.tankConfigurationRepository = tankConfigurationRepository;
        this.customerDirectory = customerDirectory;
        this.customerSiteRepository = customerSiteRepository;
    }

    @Override
    @Transactional
    public Result<Tank, ApplicationError> handle(RegisterTankCommand command) {
        if (command.organizationId() == null) {
            return Result.failure(ApplicationError.validationError("organization", "An organization is required"));
        }
        if (command.customerAccountId() == null
                || !customerDirectory.ownsCustomer(command.organizationId(), command.customerAccountId())) {
            return Result.failure(ApplicationError.validationError(
                    "customer", "The tank must belong to a customer of the same organization"));
        }
        if (command.siteId() != null) {
            var site = customerSiteRepository.findById(command.siteId());
            if (site.isEmpty()
                    || !command.organizationId().equals(site.get().getOrganizationId())
                    || !command.customerAccountId().equals(site.get().getCustomerAccountId())) {
                return Result.failure(ApplicationError.validationError(
                        "site", "The site must belong to the same customer and organization"));
            }
        }
        if (command.legacyEquipmentId() != null
                && tankRepository.findByLegacyEquipmentId(command.legacyEquipmentId()).isPresent()) {
            return Result.failure(ApplicationError.conflict("Tank", "That equipment is already mapped to a tank"));
        }

        Tank tank;
        try {
            tank = new Tank(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("tank", exception.getMessage()));
        }
        var saved = tankRepository.save(tank);
        tankConfigurationRepository.save(
                new TankConfiguration(saved.getId(), saved.getConfigurationVersion(), saved.getFuelType(), saved.getCapacity()));
        return Result.success(saved);
    }

    @Override
    @Transactional
    public Result<Tank, ApplicationError> handle(UpdateTankConfigurationCommand command) {
        var existing = tankRepository.findById(command.tankId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Tank", command.tankId().toString()));
        }
        var tank = existing.get();
        try {
            tank.applyConfiguration(command);
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("tank", exception.getMessage()));
        }
        var saved = tankRepository.save(tank);
        tankConfigurationRepository.save(
                new TankConfiguration(saved.getId(), saved.getConfigurationVersion(), saved.getFuelType(), saved.getCapacity()));
        return Result.success(saved);
    }
}
