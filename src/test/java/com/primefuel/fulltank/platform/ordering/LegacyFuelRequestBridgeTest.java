package com.primefuel.fulltank.platform.ordering;

import com.primefuel.fulltank.platform.inventory.application.commandservices.FuelProductCommandService;
import com.primefuel.fulltank.platform.inventory.domain.model.commands.CreateFuelProductCommand;
import com.primefuel.fulltank.platform.inventory.domain.model.valueobjects.FuelType;
import com.primefuel.fulltank.platform.ordering.application.internal.commandservices.LegacyFuelRequestBridge;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelRequestResource;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:legacy_request_bridge;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class LegacyFuelRequestBridgeTest {

    @Autowired
    private LegacyFuelRequestBridge fuelRequestService;

    @Autowired
    private FuelProductCommandService fuelProductCommandService;

    @Autowired
    private ReplenishmentLookup replenishmentLookup;

    private Long aProduct() {
        var product = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel puente", FuelType.DIESEL, 12.0, "GAL", 500.0, 500.0, 7L, true));
        assertThat(product.isSuccess()).isTrue();
        return product.getOrElse(null).getId();
    }

    private CreateFuelRequestResource aRequest(Long productId) {
        return new CreateFuelRequestResource(42L, 7L, null, productId, 40.0, "GAL",
                "Av. Siempre Viva 123", LocalDate.now().plusDays(1), "MANUAL");
    }

    @Test
    void legacyAcceptCreatesTheOrderOnceAndCorrelatesTheReplenishment() {
        var productId = aProduct();
        var legacy = fuelRequestService.create(aRequest(productId));

        var linked = replenishmentLookup.findByEpisodeKey("fuel-request:" + legacy.getId());
        assertThat(linked).isPresent();
        assertThat(linked.get().status()).isEqualTo("PENDING");
        assertThat(linked.get().organizationId()).isEqualTo(42L);

        var order = fuelRequestService.accept(legacy.getId());
        assertThat(order.getRequestId()).isEqualTo(legacy.getId());

        var accepted = replenishmentLookup.findByEpisodeKey("fuel-request:" + legacy.getId()).orElseThrow();
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.orderId()).isEqualTo(order.getId());

        // A second acceptance must not create a second order.
        assertThatThrownBy(() -> fuelRequestService.accept(legacy.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createRejectsInactiveProductNonPositiveQuantityAndPastDate() {
        var inactive = fuelProductCommandService.handle(new CreateFuelProductCommand(
                "Diesel inactivo", FuelType.DIESEL, 12.0, "GAL", 500.0, 500.0, 7L, false)).getOrElse(null).getId();
        var productId = aProduct();

        assertThatThrownBy(() -> fuelRequestService.create(aRequest(inactive)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fuel product is inactive");
        assertThatThrownBy(() -> fuelRequestService.create(new CreateFuelRequestResource(42L, 7L, null, productId,
                0.0, "GAL", "Av. 1", LocalDate.now().plusDays(1), "MANUAL")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Quantity must be greater than zero");
        assertThatThrownBy(() -> fuelRequestService.create(new CreateFuelRequestResource(42L, 7L, null, productId,
                40.0, "GAL", "Av. 1", LocalDate.now().minusDays(1), "MANUAL")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Delivery date cannot be in the past");
    }

    @Test
    void legacyRejectPropagatesToTheReplenishmentRequest() {
        var productId = aProduct();
        var legacy = fuelRequestService.create(aRequest(productId));

        fuelRequestService.reject(legacy.getId(), "no hay stock");

        var rejected = replenishmentLookup.findByEpisodeKey("fuel-request:" + legacy.getId()).orElseThrow();
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }
}
