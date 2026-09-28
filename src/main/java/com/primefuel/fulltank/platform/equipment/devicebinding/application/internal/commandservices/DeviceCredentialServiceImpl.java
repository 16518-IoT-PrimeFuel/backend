package com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceCredential;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.ProvisionDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RotateDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceCredentialRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.services.DeviceTokenHasher;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class DeviceCredentialServiceImpl {
    public record ProvisionedCredential(Long credentialId, String deviceId, String channel, int tokenVersion, String rawToken) { }

    private final DeviceCredentialRepository repository;

    public DeviceCredentialServiceImpl(DeviceCredentialRepository repository) {
        this.repository = repository;
    }
    @Transactional
    public Result<ProvisionedCredential, ApplicationError> handle(ProvisionDeviceCredentialCommand command) {
        if (repository.findActiveByDeviceAndChannel(command.deviceId(), command.channel()).isPresent()) {
            return Result.failure(ApplicationError.conflict(
                    "DeviceCredential", "The device channel already has an active credential; rotate it instead"));
        }
        return issue(command.deviceId(), command.channel());
    }
    @Transactional
    public Result<ProvisionedCredential, ApplicationError> handle(RotateDeviceCredentialCommand command) {
        var now = Instant.now();
        repository.findActiveByDeviceAndChannel(command.deviceId(), command.channel())
                .ifPresent(active -> {
                    active.revoke(now);
                    repository.save(active);
                });
        return issue(command.deviceId(), command.channel());
    }
    @Transactional
    public Result<Long, ApplicationError> handle(RevokeDeviceCredentialCommand command) {
        var active = repository.findActiveByDeviceAndChannel(command.deviceId(), command.channel());
        if (active.isEmpty()) {
            return Result.failure(ApplicationError.notFound(
                    "DeviceCredential", command.deviceId() + "/" + command.channel()));
        }
        var credential = active.get();
        credential.revoke(Instant.now());
        return Result.success(repository.save(credential).getId());
    }

    private Result<ProvisionedCredential, ApplicationError> issue(String deviceId, String channel) {
        if (deviceId == null || deviceId.isBlank() || channel == null || channel.isBlank()) {
            return Result.failure(ApplicationError.validationError("device", "deviceId and channel are required"));
        }
        var nextVersion = repository.findByDeviceAndChannel(deviceId, channel).stream()
                .mapToInt(DeviceCredential::getTokenVersion)
                .max()
                .orElse(0) + 1;
        var rawToken = DeviceTokenHasher.newToken();
        try {
            var saved = repository.save(new DeviceCredential(
                    deviceId, channel, DeviceTokenHasher.hash(rawToken), nextVersion, Instant.now()));
            return Result.success(new ProvisionedCredential(
                    saved.getId(), saved.getDeviceId(), saved.getChannel(), saved.getTokenVersion(), rawToken));
        } catch (IllegalArgumentException exception) {
            return Result.failure(ApplicationError.validationError("credential", exception.getMessage()));
        }
    }
}
