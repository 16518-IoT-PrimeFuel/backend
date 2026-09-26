package com.primefuel.fulltank.platform.equipment;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.CustomerBackfillServiceImpl;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.QuarantinedCompanyMappingRepository;
import com.primefuel.fulltank.platform.iam.application.commandservices.BuyerCompanyCommandService;
import com.primefuel.fulltank.platform.iam.application.commandservices.OrganizationCommandService;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateBuyerCompanyCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:customer_backfill;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
})
class CustomerBackfillTest {

    @Autowired
    private CustomerBackfillServiceImpl customerBackfillService;

    @Autowired
    private OrganizationCommandService organizationCommandService;

    @Autowired
    private BuyerCompanyCommandService buyerCompanyCommandService;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @Autowired
    private QuarantinedCompanyMappingRepository quarantineRepository;

    @Test
    void mapsUnambiguousCompaniesAndQuarantinesTheRestIdempotently() {
        var organization = organizationCommandService.handle(
                new CreateOrganizationCommand("Distribuidora A", "20444444444", OrganizationType.DISTRIBUTOR));
        assertThat(organization.isSuccess()).isTrue();
        var organizationId = organization.getOrElse(null).getId();

        var companyA = buyerCompanyCommandService.handle(
                new CreateBuyerCompanyCommand("Buyer A", "20444444444", null, "Dir A", null, null));
        var companyB = buyerCompanyCommandService.handle(
                new CreateBuyerCompanyCommand("Buyer B", "20999999999", null, "Dir B", null, null));
        assertThat(companyA.isSuccess()).isTrue();
        assertThat(companyB.isSuccess()).isTrue();

        var first = customerBackfillService.run();
        assertThat(first.legacyTotal()).isEqualTo(2);
        assertThat(first.newlyMapped()).isEqualTo(1);
        assertThat(first.newlyQuarantined()).isEqualTo(1);

        var customer = customerAccountRepository.findByLegacyCompanyId(companyA.getOrElse(null).getId());
        assertThat(customer).isPresent();
        assertThat(customer.get().getOrganizationId()).isEqualTo(organizationId);
        assertThat(quarantineRepository.findByLegacyCompanyId(companyB.getOrElse(null).getId())).isPresent();

        var second = customerBackfillService.run();
        assertThat(second.newlyMapped()).isZero();
        assertThat(second.newlyQuarantined()).isZero();
        assertThat(second.alreadyMapped()).isEqualTo(1);
        assertThat(second.alreadyQuarantined()).isEqualTo(1);
    }
}
