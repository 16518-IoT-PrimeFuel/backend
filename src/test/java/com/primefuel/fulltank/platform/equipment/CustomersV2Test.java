package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.CustomerQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetSitesByCustomerQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:customers_v2;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class CustomersV2Test {

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private CustomerQueryService customerQueryService;

    @Test
    void registersCustomersAndSitesWithinOneOrganization() {
        var customer = customerCommandService.handle(new RegisterCustomerCommand(
                1L, "Cliente Uno", "20333333333", "Av. Peru 100", "c1@example.com", "999", 55L));
        assertThat(customer.isSuccess()).isTrue();
        var customerId = customer.getOrElse(null).getId();

        var duplicate = customerCommandService.handle(new RegisterCustomerCommand(
                1L, "Cliente Uno copia", "20333333333", null, null, null, null));
        assertThat(duplicate.isFailure()).isTrue();

        var mismatchedSite = customerCommandService.handle(
                new RegisterSiteCommand(2L, customerId, "Planta Norte", "Zona 2"));
        assertThat(mismatchedSite.isFailure()).isTrue();

        var site = customerCommandService.handle(
                new RegisterSiteCommand(1L, customerId, "Planta Sur", "Zona 1"));
        assertThat(site.isSuccess()).isTrue();

        var sites = customerQueryService.handle(new GetSitesByCustomerQuery(customerId));
        assertThat(sites).hasSize(1);
        assertThat(sites.getFirst().getOrganizationId()).isEqualTo(1L);
    }
}
