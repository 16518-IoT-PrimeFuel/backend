package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.EquipmentCommandService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankBackfillService;
import com.primefuel.fulltank.platform.equipment.application.commandservices.TankReadingService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.CreateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.UpdateEquipmentCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.EquipmentType;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:tank_backfill;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class TankLegacyBackfillTest {

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private TankBackfillService tankBackfillService;

    @Autowired
    private TankReadingService tankReadingService;

    @Autowired
    private EquipmentCommandService equipmentCommandService;

    @Autowired
    private TankRepository tankRepository;

    private Long legacyEquipment(String name, Double capacity, Double level, Long companyId, boolean withFuel) {
        var created = equipmentCommandService.handle(new CreateEquipmentCommand(
                name, EquipmentType.TRUCK, "ABC-123", withFuel ? FuelType.DIESEL : null,
                capacity, level, "Lima", "operational", false, 20, null, companyId, null));
        assertThat(created.isSuccess()).isTrue();
        return created.getOrElse(null).getId();
    }

    @Test
    void backfillsOnlyMappableEquipmentIdempotentlyAndKeepsReadingsMonotonic() {
        var customer = customerCommandService.handle(
                new RegisterCustomerCommand(1L, "Cliente Flota", "20666666666", null, null, null, 42L));
        assertThat(customer.isSuccess()).isTrue();

        var mappableId = legacyEquipment("Camion A", 500.0, 120.0, 42L, true);
        legacyEquipment("Maquina sin capacidad", 0.0, 0.0, 42L, true);
        legacyEquipment("Camion sin cliente", 300.0, 50.0, 99L, true);

        var first = tankBackfillService.run();
        assertThat(first.equipmentTotal()).isEqualTo(3);
        assertThat(first.created()).isEqualTo(1);
        assertThat(first.unmappable()).isEqualTo(1);
        assertThat(first.withoutCustomer()).isEqualTo(1);
        assertThat(first.levelMismatches()).isZero();

        var tankId = tankRepository.findByLegacyEquipmentId(mappableId).orElseThrow().getId();

        var second = tankBackfillService.run();
        assertThat(second.created()).isZero();
        assertThat(second.alreadyMapped()).isEqualTo(1);

        // Out-of-order (older) reading must not regress the snapshot.
        var older = tankReadingService.applyValidatedReading(tankId, 400.0, "LITRE", Instant.now().minusSeconds(600));
        assertThat(older.isSuccess()).isTrue();
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(120.0);

        var newer = tankReadingService.applyValidatedReading(tankId, 250.0, "LITRE", Instant.now().plusSeconds(60));
        assertThat(newer.isSuccess()).isTrue();
        assertThat(tankRepository.findById(tankId).orElseThrow().getCurrentLevel().amount()).isEqualTo(250.0);
        assertThat(tankRepository.findById(tankId).orElseThrow().getLevelSource()).isEqualTo("VALIDATED");

        // v1 adaptation: editing the legacy equipment level reaches the mapped tank as a manual edit.
        equipmentCommandService.handle(new UpdateEquipmentCommand(
                mappableId, "Camion A", EquipmentType.TRUCK, "ABC-123", FuelType.DIESEL,
                500.0, 310.0, "Lima", "operational", false, 20, null, null));
        var tank = tankRepository.findById(tankId).orElseThrow();
        assertThat(tank.getCurrentLevel().amount()).isEqualTo(310.0);
        assertThat(tank.getLevelSource()).isEqualTo("MANUAL");
    }
}
