package com.primefuel.fulltank.platform.supply;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.supply.api.SupplyCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:supply_catalog;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class SupplyCatalogTest {

    @Autowired
    private SupplyCatalog supplyCatalog;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @Test
    void filtersByTenantAndActiveFlag() {
        var activeForSeven = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel 7", FuelType.DIESEL, 15.0, "GAL", 100.0, 500.0, 7L, true));
        var inactiveForSeven = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "GLP 7 (inactivo)", FuelType.GLP, 8.0, "GAL", 40.0, 200.0, 7L, false));
        var otherTenant = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel 8", FuelType.DIESEL, 14.0, "GAL", 90.0, 500.0, 8L, true));
        assertThat(activeForSeven.isSuccess()).isTrue();
        assertThat(inactiveForSeven.isSuccess()).isTrue();
        assertThat(otherTenant.isSuccess()).isTrue();

        var activeOnly = supplyCatalog.listForTenant(7L, true);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.getFirst().fuelProductId()).isEqualTo(activeForSeven.getOrElse(null).getId());
        assertThat(activeOnly.getFirst().unit()).isEqualTo("GALLON");
        assertThat(activeOnly.getFirst().stock()).isEqualTo(100.0);

        assertThat(supplyCatalog.listForTenant(7L, false)).hasSize(2);

        var otherTenantId = otherTenant.getOrElse(null).getId();
        assertThat(supplyCatalog.findForTenant(7L, otherTenantId)).isEmpty();
        assertThat(supplyCatalog.findForTenant(8L, otherTenantId)).isPresent();
    }
}
