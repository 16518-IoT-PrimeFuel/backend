package com.primefuel.fulltank.platform.contract;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.CreateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.EquipmentType;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import java.util.concurrent.atomic.AtomicLong;

/** Test fixture for the supported v2 request lifecycle with real legacy company/equipment mappings. */
public final class ReplenishmentTestFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private ReplenishmentTestFixtures() { }

    public static Request create(CustomerCommandService customers, EquipmentCommandService equipment,
                                 TankCommandService tanks, Long legacyCompanyId, String address) {
        var id = SEQUENCE.incrementAndGet();
        var customer = customers.handle(new RegisterCustomerCommand(legacyCompanyId, "Fixture " + id,
                null, null, null, null, legacyCompanyId)).getOrElse(null);
        var site = customers.handle(new RegisterSiteCommand(legacyCompanyId, customer.getId(),
                "Sitio " + id, address)).getOrElse(null);
        var oldEquipment = equipment.handle(new CreateEquipmentCommand("Equipo " + id, EquipmentType.TRUCK,
                "FIX-" + id, FuelType.DIESEL, 1000.0, 0.0, "Lima", "AVAILABLE", false,
                10, null, legacyCompanyId, null)).getOrElse(null);
        var tank = tanks.handle(new RegisterTankCommand(legacyCompanyId, customer.getId(), site.getId(),
                "Cisterna " + id, "DIESEL", 1000.0, "LITRE", 0.0, oldEquipment.getId())).getOrElse(null);
        return new Request(legacyCompanyId, customer.getId(), tank.getId(), address);
    }

    public record Request(Long organizationId, Long customerId, Long tankId, String address) { }
}
