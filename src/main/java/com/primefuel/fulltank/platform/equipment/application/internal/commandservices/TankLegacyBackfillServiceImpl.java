package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.TankBackfillReport;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankBackfillService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.api.CustomerDirectory;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.equipment.domain.services.TankEligibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent backfill of legacy Equipment into Tank rows. Only classifiable equipment (positive
 * capacity + fuel type) whose company resolves to a mapped customer becomes a Tank; everything else is
 * left untouched and reported.
 */
@Service
public class TankLegacyBackfillServiceImpl implements TankBackfillService {

    private final EquipmentRepository equipmentRepository;
    private final TankRepository tankRepository;
    private final CustomerDirectory customerDirectory;
    private final TankCommandService tankCommandService;

    public TankLegacyBackfillServiceImpl(EquipmentRepository equipmentRepository,
                                         TankRepository tankRepository,
                                         CustomerDirectory customerDirectory,
                                         TankCommandService tankCommandService) {
        this.equipmentRepository = equipmentRepository;
        this.tankRepository = tankRepository;
        this.customerDirectory = customerDirectory;
        this.tankCommandService = tankCommandService;
    }

    @Override
    @Transactional
    public TankBackfillReport run() {
        var equipmentItems = equipmentRepository.findAll();
        long created = 0;
        long alreadyMapped = 0;
        long unmappable = 0;
        long withoutCustomer = 0;
        long levelMismatches = 0;

        for (var equipment : equipmentItems) {
            if (tankRepository.findByLegacyEquipmentId(equipment.getId()).isPresent()) {
                alreadyMapped++;
                continue;
            }
            if (!TankEligibility.isMappable(equipment.getTankCapacity(), equipment.hasFuelType())) {
                unmappable++;
                continue;
            }
            var customerId = customerDirectory.customerIdForLegacyCompany(equipment.getCompanyId());
            if (customerId.isEmpty()) {
                withoutCustomer++;
                continue;
            }
            var organizationId = customerDirectory.organizationIdForCustomer(customerId.get());
            if (organizationId.isEmpty()) {
                withoutCustomer++;
                continue;
            }
            // Legacy equipment carries no unit and its fuel type is not copied here (reading the
            // inventory FuelType from equipment would grow the frozen module baseline); both are left
            // for the configuration/telemetry path. Documented as T06-B assumption A1.
            var result = tankCommandService.handle(new RegisterTankCommand(
                    organizationId.get(), customerId.get(), null, equipment.getName(),
                    null, equipment.getTankCapacity(), null,
                    equipment.getCurrentLevel(), equipment.getId()));
            if (result.isFailure()) {
                withoutCustomer++;
                continue;
            }
            created++;
            var tankLevel = result.getOrElse(null).getCurrentLevel().amount();
            var legacyLevel = equipment.getCurrentLevel() == null ? 0.0 : equipment.getCurrentLevel();
            if (Math.abs(tankLevel - legacyLevel) > 1e-9) {
                levelMismatches++;
            }
        }
        return new TankBackfillReport(equipmentItems.size(), created, alreadyMapped, unmappable,
                withoutCustomer, levelMismatches);
    }
}
