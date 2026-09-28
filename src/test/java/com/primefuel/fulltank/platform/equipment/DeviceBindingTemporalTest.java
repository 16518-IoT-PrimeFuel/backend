package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.api.ActiveBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.queryservices.DeviceBindingQueryService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.BindDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.MoveDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.events.DeviceBoundEvent;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetActiveBindingQuery;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.queries.GetBindingsByDeviceQuery;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.valueobjects.BindingStatus;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:device_bindings;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jdbc.auto-commit=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class DeviceBindingTemporalTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T00:00:00Z");

    @Autowired
    private DeviceBindingCommandService commandService;

    @Autowired
    private DeviceBindingQueryService queryService;

    @Autowired
    private DeviceBindingRepository repository;

    @Autowired
    private ActiveBinding activeBinding;

    @Test
    void rejectsOverlapAndAttributesReadingsByInstant() {
        var bound = commandService.handle(new BindDeviceCommand(1L, "dev-1", "tank-level", 100L, T0));
        assertThat(bound.isSuccess()).isTrue();
        assertThat(bound.getOrElse(null).getStatus()).isEqualTo(BindingStatus.ACTIVE);

        // Events are raised on the aggregate itself (publication through the outbox is the deferred
        // part already documented in T19-B); they never carry a credential.
        var raised = new DeviceBinding(1L, "dev-1", "tank-level", 100L, T0);
        assertThat(raised.domainEvents()).hasAtLeastOneElementOfType(DeviceBoundEvent.class);

        // A second open binding for the same channel is refused by the domain check.
        var duplicateOpen = commandService.handle(new BindDeviceCommand(1L, "dev-1", "tank-level", 200L, T0));
        assertThat(duplicateOpen.isFailure()).isTrue();

        // A historical period that would overlap an existing one is refused too.
        var revoked = commandService.handle(
                new RevokeDeviceCommand(bound.getOrElse(null).getId(), T1));
        assertThat(revoked.isSuccess()).isTrue();

        var overlapping = commandService.handle(
                new BindDeviceCommand(1L, "dev-1", "tank-level", 300L, T0.plusSeconds(60)));
        assertThat(overlapping.isFailure()).isTrue();

        // Half-open interval: the closing instant is already outside the binding.
        assertThat(queryService.handle(new GetActiveBindingQuery("dev-1", "tank-level", T1.minusSeconds(1))))
                .isPresent();
        assertThat(queryService.handle(new GetActiveBindingQuery("dev-1", "tank-level", T1))).isEmpty();
        assertThat(activeBinding.activeAt("dev-1", "tank-level", T1)).isEmpty();

        // A new, non-overlapping period is accepted and attributed at its own instant.
        var rebind = commandService.handle(new BindDeviceCommand(1L, "dev-1", "tank-level", 400L, T2));
        assertThat(rebind.isSuccess()).isTrue();
        var atT2 = activeBinding.activeAt("dev-1", "tank-level", T2);
        assertThat(atT2).isPresent();
        assertThat(atT2.get().tankId()).isEqualTo(400L);
        assertThat(atT2.get().organizationId()).isEqualTo(1L);
    }

    @Test
    void databaseConstraintIsTheBackstopAgainstTwoOpenBindings() {
        var first = commandService.handle(new BindDeviceCommand(2L, "dev-2", "tank-level", 10L, T0));
        assertThat(first.isSuccess()).isTrue();

        var rogue = new DeviceBinding(2L, "dev-2", "tank-level", 20L, T1);
        assertThatThrownBy(() -> repository.save(rogue))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void moveClosesThePeriodAndOpensTheNext() {
        var bound = commandService.handle(new BindDeviceCommand(3L, "dev-3", "tank-level", 500L, T0));
        var bindingId = bound.getOrElse(null).getId();

        var moved = commandService.handle(new MoveDeviceCommand(bindingId, 600L, T1));
        assertThat(moved.isSuccess()).isTrue();
        assertThat(moved.getOrElse(null).getTankId()).isEqualTo(600L);

        var history = queryService.handle(new GetBindingsByDeviceQuery("dev-3", "tank-level"));
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().getStatus()).isEqualTo(BindingStatus.CLOSED);
        assertThat(history.getFirst().getValidTo()).isEqualTo(T1);
        assertThat(history.getLast().getStatus()).isEqualTo(BindingStatus.ACTIVE);

        // Before the move the reading belongs to the old tank; at the move instant, to the new one.
        assertThat(activeBinding.activeAt("dev-3", "tank-level", T0.plusSeconds(30)).orElseThrow().tankId())
                .isEqualTo(500L);
        assertThat(activeBinding.activeAt("dev-3", "tank-level", T1).orElseThrow().tankId()).isEqualTo(600L);

        // Moving an already closed binding is a conflict.
        assertThat(commandService.handle(new MoveDeviceCommand(bindingId, 700L, T2)).isFailure()).isTrue();
    }
}
