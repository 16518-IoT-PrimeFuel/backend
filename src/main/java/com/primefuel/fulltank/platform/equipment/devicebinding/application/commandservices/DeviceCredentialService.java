package com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices;

import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.ProvisionDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RotateDeviceCredentialCommand;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

public interface DeviceCredentialService {

    Result<ProvisionedCredential, ApplicationError> handle(ProvisionDeviceCredentialCommand command);

    Result<ProvisionedCredential, ApplicationError> handle(RotateDeviceCredentialCommand command);

    Result<Long, ApplicationError> handle(RevokeDeviceCredentialCommand command);

    /** The raw token is present only in the response of a provision/rotate operation. */
    record ProvisionedCredential(
            Long credentialId,
            String deviceId,
            String channel,
            int tokenVersion,
            String rawToken) {
    }
}
