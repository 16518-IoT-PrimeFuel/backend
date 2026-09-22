package com.primefuel.fulltank.platform.equipment.infrastructure.services;

import com.primefuel.fulltank.platform.equipment.api.ActiveBinding;
import com.primefuel.fulltank.platform.equipment.api.DeviceAuthentication;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.DeviceAuthenticationOutcome;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceCredentialRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.services.DeviceTokenHasher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Machine authentication: verifies the rotating credential and then resolves the tank bound at the
 * reading's instant. Any other outcome means the reading must be quarantined by the caller.
 */
@Component("deviceAuthentication")
public class DeviceAuthenticationImpl implements DeviceAuthentication {

    private final DeviceCredentialRepository credentialRepository;
    private final ActiveBinding activeBinding;

    public DeviceAuthenticationImpl(DeviceCredentialRepository credentialRepository,
                                    ActiveBinding activeBinding) {
        this.credentialRepository = credentialRepository;
        this.activeBinding = activeBinding;
    }

    @Override
    public Decision authenticate(String deviceId, String channel, String token, Instant instant) {
        if (deviceId == null || channel == null || token == null || instant == null) {
            return Decision.rejected(DeviceAuthenticationOutcome.UNKNOWN_CREDENTIAL.name());
        }
        var credential = credentialRepository.findByTokenHash(DeviceTokenHasher.hash(token));
        if (credential.isEmpty()
                || !deviceId.equals(credential.get().getDeviceId())
                || !channel.equals(credential.get().getChannel())) {
            return Decision.rejected(DeviceAuthenticationOutcome.UNKNOWN_CREDENTIAL.name());
        }
        if (!credential.get().isActive()) {
            return Decision.rejected(DeviceAuthenticationOutcome.REVOKED_CREDENTIAL.name());
        }
        return activeBinding.activeAt(deviceId, channel, instant)
                .map(binding -> new Decision(DeviceAuthenticationOutcome.AUTHENTICATED.name(),
                        binding.tankId(), binding.organizationId()))
                .orElseGet(() -> Decision.rejected(DeviceAuthenticationOutcome.NO_ACTIVE_BINDING.name()));
    }
}
