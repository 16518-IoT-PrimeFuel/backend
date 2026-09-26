package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TankReadingServiceImpl;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Equipment;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.CreateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.EquipmentRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentCommandServiceImpl implements EquipmentCommandService {

    private final EquipmentRepository equipmentRepository;
    private final TankRepository tankRepository;
    private final TankReadingServiceImpl tankReadingService;

    public EquipmentCommandServiceImpl(EquipmentRepository equipmentRepository,
                                       TankRepository tankRepository,
                                       TankReadingServiceImpl tankReadingService) {
        this.equipmentRepository = equipmentRepository;
        this.tankRepository = tankRepository;
        this.tankReadingService = tankReadingService;
    }

    @Override
    public Result<Equipment, ApplicationError> handle(CreateEquipmentCommand command) {
        var equipment = new Equipment(command);
        var saved = equipmentRepository.save(equipment);
        return Result.success(saved);
    }

    @Override
    @Transactional
    public Result<Equipment, ApplicationError> handle(UpdateEquipmentCommand command) {
        var existing = equipmentRepository.findById(command.equipmentId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("Equipment", command.equipmentId().toString()));
        }
        var equipment = existing.get();
        equipment.update(command);
        var saved = equipmentRepository.save(equipment);

        // v1 compatibility bridge: a mapped tank mirrors the level, but as a MANUAL edit, not as a
        // validated reading, so telemetry precedence is preserved.
        if (command.currentLevel() != null) {
            tankRepository.findByLegacyEquipmentId(saved.getId())
                    .ifPresent(tank -> tankReadingService.applyManualLevel(
                            tank.getId(), command.currentLevel(), tank.getCapacity().unit().name()));
        }
        return Result.success(saved);
    }
}
