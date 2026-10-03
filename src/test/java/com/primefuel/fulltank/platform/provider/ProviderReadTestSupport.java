package com.primefuel.fulltank.platform.provider;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerAccount;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.CustomerSite;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterTankCommand;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerAccountRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.BuyerCompany;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.User;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateBuyerCompanyCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.CreateOrganizationCommand;
import com.primefuel.fulltank.platform.iam.domain.model.commands.GrantMembershipCommand;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.MembershipRole;
import com.primefuel.fulltank.platform.iam.domain.model.valueobjects.OrganizationType;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.MembershipRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.OrganizationRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.model.UserDetailsImpl;
import com.primefuel.fulltank.platform.ordering.domain.model.aggregates.FuelOrder;
import com.primefuel.fulltank.platform.ordering.domain.model.valueobjects.OrderStatus;
import com.primefuel.fulltank.platform.ordering.domain.repositories.FuelOrderRepository;
import com.primefuel.fulltank.platform.replenishment.domain.model.aggregates.ReplenishmentRequest;
import com.primefuel.fulltank.platform.replenishment.domain.model.commands.CreateReplenishmentRequestCommand;
import com.primefuel.fulltank.platform.replenishment.domain.repositories.ReplenishmentRequestRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:provider_reads;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "authorization.jwt.secret=0123456789abcdef0123456789abcdef"
        })
@AutoConfigureMockMvc
abstract class ProviderReadTestSupport {
    @Autowired MockMvc mvc;
    @Autowired BuyerCompanyDirectory companies;
    @Autowired LegacyCompanyDirectory legacy;
    @Autowired CustomerAccountRepository accounts;
    @Autowired CustomerSiteRepository sites;
    @Autowired TankRepository tanks;
    @Autowired FuelOrderRepository orders;
    @Autowired ReplenishmentRequestRepository requests;
    @Autowired BuyerCompanyRepository buyerCompanies;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    private static final AtomicLong IDS = new AtomicLong(8000);

    record Fixture(long provider, long company, long org, long account, long tank, long site) {}

    Fixture fixture(boolean withOrder, boolean withRequest) {
        long provider = IDS.incrementAndGet();
        String ruc = "20" + String.format("%09d", provider);
        var companyEntity =
                buyerCompanies.save(
                        new BuyerCompany(
                                new CreateBuyerCompanyCommand(
                                        "Buyer " + provider,
                                        ruc,
                                        "Industry",
                                        "Lima",
                                        "buyer@example.test",
                                        "999999999")));
        var organizationEntity =
                organizations.save(
                        new Organization(
                                new CreateOrganizationCommand(
                                        "Buyer " + provider, ruc, OrganizationType.CUSTOMER)));
        long company = companyEntity.getId(), organization = organizationEntity.getId();
        var user = new User("buyer-" + provider, "encoded");
        user.setCompanyId(company);
        user = users.save(user);
        memberships.save(
                new Membership(
                        new GrantMembershipCommand(
                                organization, user.getId(), MembershipRole.OWNER)));
        var account =
                accounts.save(
                        new CustomerAccount(
                                new RegisterCustomerCommand(
                                        organization,
                                        "Buyer " + company,
                                        "RUC" + company,
                                        "Lima",
                                        "test@example.test",
                                        "999999999",
                                        company)));
        var site =
                sites.save(
                        new CustomerSite(
                                new RegisterSiteCommand(
                                        organization, account.getId(), "Plant", "Lima")));
        var tank =
                tanks.save(
                        new Tank(
                                new RegisterTankCommand(
                                        organization,
                                        account.getId(),
                                        site.getId(),
                                        "Diesel tank",
                                        "DIESEL",
                                        1000.0,
                                        "LITRE",
                                        150.0,
                                        null)));
        if (withOrder) {
            var order = new FuelOrder();
            order.setCompanyId(company);
            order.setProviderId(provider);
            order.setFuelProductId(1L);
            order.setRequestedQuantity(100.0);
            order.setTotalPrice(200.0);
            order.setStatus(OrderStatus.PENDING);
            order.setDeliveryAddress("Lima");
            order.setScheduledDate(LocalDate.of(2026, 10, 2));
            orders.save(order);
        }
        if (withRequest)
            requests.save(
                    new ReplenishmentRequest(
                            new CreateReplenishmentRequestCommand(
                                    organization,
                                    account.getId(),
                                    tank.getId(),
                                    provider,
                                    1L,
                                    200.0,
                                    "LITRE",
                                    null,
                                    null,
                                    "Lima",
                                    LocalDate.of(2026, 10, 2)),
                            2.0));
        return new Fixture(
                provider, company, organization, account.getId(), tank.getId(), site.getId());
    }

    static RequestPostProcessor provider(long id) {
        return auth(id, "ROLE_PROVIDER");
    }

    static RequestPostProcessor auth(long id, String role) {
        var p =
                new UserDetailsImpl(
                        id,
                        "provider-" + id,
                        "encoded",
                        null,
                        id,
                        List.of(new SimpleGrantedAuthority(role)));
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
}
