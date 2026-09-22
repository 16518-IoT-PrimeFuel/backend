package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateTankConfigurationCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.TankClassification;
import com.primefuel.fulltank.platform.equipment.domain.services.TankEligibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:tank_model;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class TankModelTest {

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private TankCommandService tankCommandService;

    private Long aCustomer() {
        var customer = customerCommandService.handle(
                new RegisterCustomerCommand(1L, "Cliente Tanque", "20555555555", null, null, null, null));
        assertThat(customer.isSuccess()).isTrue();
        return customer.getOrElse(null).getId();
    }

    @Test
    void registersTankWithVersionedConfigurationAndEnforcesRange() {
        var customerId = aCustomer();

        var tank = tankCommandService.handle(new RegisterTankCommand(
                1L, customerId, null, "Tanque 1", "DIESEL", 500.0, "GAL", 100.0, null));
        assertThat(tank.isSuccess()).isTrue();
        assertThat(tank.getOrElse(null).getClassification()).isEqualTo(TankClassification.NATIVE);
        assertThat(tank.getOrElse(null).getConfigurationVersion()).isEqualTo(1);

        var outOfRange = tankCommandService.handle(new RegisterTankCommand(
                1L, customerId, null, "Tanque malo", "DIESEL", 100.0, "GAL", 200.0, null));
        assertThat(outOfRange.isFailure()).isTrue();

        var foreignCustomer = tankCommandService.handle(new RegisterTankCommand(
                2L, customerId, null, "Ajeno", "DIESEL", 100.0, "GAL", 0.0, null));
        assertThat(foreignCustomer.isFailure()).isTrue();

        var tankId = tank.getOrElse(null).getId();
        var reconfigured = tankCommandService.handle(
                new UpdateTankConfigurationCommand(tankId, "GASOLINE_95", 2000.0, "LITRE"));
        assertThat(reconfigured.isSuccess()).isTrue();
        assertThat(reconfigured.getOrElse(null).getConfigurationVersion()).isEqualTo(2);
        assertThat(reconfigured.getOrElse(null).getCapacity().unit().name()).isEqualTo("LITRE");
    }

    @Test
    void classifiesOnlyEquipmentWithCapacityAndFuel() {
        assertThat(TankEligibility.isMappable(100.0, true)).isTrue();
        assertThat(TankEligibility.isMappable(0.0, true)).isFalse();
        assertThat(TankEligibility.isMappable(null, true)).isFalse();
        assertThat(TankEligibility.isMappable(100.0, false)).isFalse();
    }
}
