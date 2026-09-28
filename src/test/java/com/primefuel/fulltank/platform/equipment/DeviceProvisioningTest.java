package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.api.DeviceAuthentication;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.commandservices.DeviceCredentialServiceImpl;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.BindDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.MoveDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.ProvisionDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RotateDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceCredentialRepository;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.services.DeviceTokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:device_provisioning;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeviceProvisioningTest {

    private static final String DEVICE = "dev-9";
    private static final String CHANNEL = "tank-level";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired
    private DeviceCredentialServiceImpl credentialService;

    @Autowired
    private DeviceBindingCommandService bindingService;

    @Autowired
    private DeviceAuthentication deviceAuthentication;

    @Autowired
    private DeviceCredentialRepository credentialRepository;

    @Test
    void rotatesCredentialsAndQuarantinesUntilBindingExists() {
        var provisioned = credentialService.handle(new ProvisionDeviceCredentialCommand(DEVICE, CHANNEL));
        assertThat(provisioned.isSuccess()).isTrue();
        var firstToken = provisioned.getOrElse(null).rawToken();
        assertThat(firstToken).isNotBlank();

        // Only the hash is persisted; the raw token is never stored.
        assertThat(credentialRepository.findByTokenHash(DeviceTokenHasher.hash(firstToken))).isPresent();
        assertThat(credentialRepository.findByTokenHash(firstToken)).isEmpty();

        // Valid credential but no binding yet -> quarantine.
        var unbound = deviceAuthentication.authenticate(DEVICE, CHANNEL, firstToken, T0);
        assertThat(unbound.authenticated()).isFalse();
        assertThat(unbound.outcome()).isEqualTo("NO_ACTIVE_BINDING");

        // A wrong token is never accepted.
        var forged = deviceAuthentication.authenticate(DEVICE, CHANNEL, "not-a-real-token", T0);
        assertThat(forged.outcome()).isEqualTo("UNKNOWN_CREDENTIAL");

        // A second credential for the same channel must be rotated, not provisioned.
        assertThat(credentialService.handle(new ProvisionDeviceCredentialCommand(DEVICE, CHANNEL)).isFailure())
                .isTrue();

        var bound = bindingService.handle(new BindDeviceCommand(1L, DEVICE, CHANNEL, 100L, T0));
        assertThat(bound.isSuccess()).isTrue();

        var authenticated = deviceAuthentication.authenticate(DEVICE, CHANNEL, firstToken, T0);
        assertThat(authenticated.authenticated()).isTrue();
        assertThat(authenticated.resolvedTankId()).contains(100L);
        assertThat(authenticated.organizationId()).isEqualTo(1L);

        // Rotation invalidates the previous token immediately.
        var rotated = credentialService.handle(new RotateDeviceCredentialCommand(DEVICE, CHANNEL));
        assertThat(rotated.isSuccess()).isTrue();
        var secondToken = rotated.getOrElse(null).rawToken();
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(deviceAuthentication.authenticate(DEVICE, CHANNEL, firstToken, T0).outcome())
                .isEqualTo("REVOKED_CREDENTIAL");

        // Boundary: after a move, attribution follows the instant, not the current binding.
        var moved = bindingService.handle(new MoveDeviceCommand(bound.getOrElse(null).getId(), 200L, T1));
        assertThat(moved.isSuccess()).isTrue();
        assertThat(deviceAuthentication.authenticate(DEVICE, CHANNEL, secondToken, T1).resolvedTankId())
                .contains(200L);
        assertThat(deviceAuthentication.authenticate(DEVICE, CHANNEL, secondToken, T1.minusSeconds(1))
                .resolvedTankId()).contains(100L);

        // Revocation stops authentication entirely.
        assertThat(credentialService.handle(new RevokeDeviceCredentialCommand(DEVICE, CHANNEL)).isSuccess()).isTrue();
        assertThat(deviceAuthentication.authenticate(DEVICE, CHANNEL, secondToken, T1).outcome())
                .isEqualTo("REVOKED_CREDENTIAL");
        assertThat(credentialService.handle(new RevokeDeviceCredentialCommand(DEVICE, CHANNEL)).isFailure()).isTrue();
    }

    @Test
    void overlappingMovesAreRejected() {
        var provisioned = credentialService.handle(
                new ProvisionDeviceCredentialCommand("dev-overlap", CHANNEL));
        assertThat(provisioned.isSuccess()).isTrue();

        var bound = bindingService.handle(new BindDeviceCommand(2L, "dev-overlap", CHANNEL, 10L, T0));
        var bindingId = bound.getOrElse(null).getId();
        assertThat(bindingService.handle(new MoveDeviceCommand(bindingId, 20L, T1)).isSuccess()).isTrue();

        // The closed period can no longer be extended over the new one.
        var overlapping = bindingService.handle(
                new BindDeviceCommand(2L, "dev-overlap", CHANNEL, 30L, T0.plusSeconds(10)));
        assertThat(overlapping.isFailure()).isTrue();
    }
}
