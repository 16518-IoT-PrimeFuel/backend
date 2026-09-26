package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.application.ports.TankData;
import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.CustomerTanksController;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerTanksControllerTest {
    private final TankCommandService commands = mock(TankCommandService.class);
    private final TankStore tanks = mock(TankStore.class);
    private final CustomerSiteCommandService sites = mock(CustomerSiteCommandService.class);
    private final CustomerTanksController controller = new CustomerTanksController(commands, tanks, sites);

    @Test
    void listsCurrentTankStateForAnOwnedSite() {
        when(sites.siteBelongsToCompany(7L, 3L)).thenReturn(true);
        when(tanks.findBySiteId(7L)).thenReturn(List.of(new TankData(9L, 7L, "Main tank", "DIESEL",
                1000.0, "L", 420.0, "ACTIVE", Instant.parse("2026-09-26T10:00:00Z"))));

        var response = controller.list(3L, 7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var state = (com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.TankStateResource)
                ((java.util.List<?>) response.getBody()).getFirst();
        assertEquals(420.0, state.currentLevel());
    }

    @Test
    void hidesTankFromAnotherSite() {
        when(sites.siteBelongsToCompany(7L, 3L)).thenReturn(true);
        when(tanks.findById(9L)).thenReturn(java.util.Optional.of(new TankData(9L, 8L, "Other", "DIESEL",
                1000.0, "L", 420.0, "ACTIVE", null)));

        assertEquals(HttpStatus.NOT_FOUND, controller.get(3L, 7L, 9L).getStatusCode());
    }
}
