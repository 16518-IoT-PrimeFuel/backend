package com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.BindDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.MoveDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DeviceBindingCommandServiceImpl implements DeviceBindingCommandService {

    private final DeviceBindingRepository repository;

    public DeviceBindingCommandServiceImpl(DeviceBindingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Result<DeviceBinding, ApplicationError> handle(BindDeviceCommand command) {
        if (command.organizationId() == null) {
            return Result.failure(ApplicationError.validationError("organization", "An organization is required"));
        }
        if (hasOverlap(command.deviceId(), command.channel(), command.validFrom(), null)) {
            return Result.failure(ApplicationError.conflict(
                    "DeviceBinding", "The device channel is already bound over that period"));
        }
        try {
            var binding = new DeviceBinding(command.organizationId(), command.deviceId(), command.channel(),
                    command.tankId(), command.validFrom());
            return Result.success(repository.save(binding));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("binding", exception.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            // Database backstop: unique (device_id, channel, active_slot) forbids two open bindings.
            return Result.failure(ApplicationError.conflict(
                    "DeviceBinding", "The device channel already has an open binding"));
        }
    }

    @Override
    @Transactional
    public Result<DeviceBinding, ApplicationError> handle(RevokeDeviceCommand command) {
        var existing = repository.findById(command.bindingId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("DeviceBinding", String.valueOf(command.bindingId())));
        }
        var binding = existing.get();
        try {
            binding.revoke(command.at());
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("binding", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("DeviceBinding", exception.getMessage()));
        }
        return Result.success(repository.save(binding));
    }

    @Override
    @Transactional
    public Result<DeviceBinding, ApplicationError> handle(MoveDeviceCommand command) {
        var existing = repository.findById(command.bindingId());
        if (existing.isEmpty()) {
            return Result.failure(ApplicationError.notFound("DeviceBinding", String.valueOf(command.bindingId())));
        }
        try {
            var next = existing.get().moveTo(command.newTankId(), command.at());
            repository.save(existing.get());
            return Result.success(repository.save(next));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("binding", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return Result.failure(ApplicationError.conflict("DeviceBinding", exception.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            return Result.failure(ApplicationError.conflict(
                    "DeviceBinding", "The device channel already has an open binding"));
        }
    }

    private boolean hasOverlap(String deviceId, String channel, Instant from, Instant to) {
        if (deviceId == null || channel == null) {
            return false;
        }
        return repository.findByDeviceAndChannel(deviceId, channel).stream()
                .anyMatch(existing -> existing.overlaps(from, to));
    }
}
