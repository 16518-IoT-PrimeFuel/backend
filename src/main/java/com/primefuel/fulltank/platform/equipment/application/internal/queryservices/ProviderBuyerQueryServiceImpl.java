package com.primefuel.fulltank.platform.equipment.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.application.queryservices.ProviderBuyerQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderBuyerCompany;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.ProviderBuyerLinkRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
import com.primefuel.fulltank.platform.replenishment.api.ReplenishmentLookup;
import com.primefuel.fulltank.platform.replenishment.api.TankRefillLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProviderBuyerQueryServiceImpl
        implements ProviderBuyerQueryService, ProviderBuyerAccess {
    private final ProviderBuyerLinkRepository links;
    private final OrderLookup orders;
    private final ReplenishmentLookup requests;
    private final BuyerCompanyDirectory companies;
    private final LegacyCompanyDirectory legacy;
    private final TankRepository tanks;
    private final TankRefillLookup policies;
    private final CustomerSiteRepository sites;

    public ProviderBuyerQueryServiceImpl(
            OrderLookup orders,
            ReplenishmentLookup requests,
            BuyerCompanyDirectory companies,
            LegacyCompanyDirectory legacy,
            TankRepository tanks,
            TankRefillLookup policies,
            CustomerSiteRepository sites,
            ProviderBuyerLinkRepository links) {
        this.orders = orders;
        this.requests = requests;
        this.companies = companies;
        this.legacy = legacy;
        this.tanks = tanks;
        this.policies = policies;
        this.sites = sites;
        this.links = links;
    }

    public List<ProviderBuyerCompany> handle(GetProviderBuyerCompaniesQuery query) {
        var provider = query.providerId();
        var ownOrders =
                orders.findByProviderId(provider).stream()
                        .filter(o -> provider.equals(o.providerId()))
                        .toList();
        var ids = new TreeSet<Long>();
        links.findByProviderId(provider).stream().map(l -> l.getBuyerCompanyId()).forEach(ids::add);
        ownOrders.stream()
                .map(OrderLookup.OrderSnapshot::companyId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        requests.findByProviderId(provider).stream()
                .filter(r -> provider.equals(r.providerId()))
                .map(ReplenishmentLookup.ReplenishmentView::organizationId)
                .distinct()
                .forEach(org -> legacy.buyerCompanyIdForOrganization(org).ifPresent(ids::add));
        return ids.stream()
                .map(companies::findById)
                .flatMap(Optional::stream)
                .map(
                        c -> {
                            var org = linkedBuyerOrganization(provider, c.id()).orElse(null);
                            var assets =
                                    org == null
                                            ? List.<Tank>of()
                                            : tanks.findByOrganizationId(org).stream()
                                                    .filter(
                                                            t ->
                                                                    org.equals(
                                                                                    t
                                                                                            .getOrganizationId())
                                                                            && t.isActive())
                                                    .toList();
                            var history =
                                    ownOrders.stream()
                                            .filter(o -> c.id().equals(o.companyId()))
                                            .toList();
                            long active =
                                    history.stream()
                                            .filter(
                                                    o ->
                                                            Set.of(
                                                                            "PENDING",
                                                                            "CONFIRMED",
                                                                            "DISPATCHED",
                                                                            "IN_PROGRESS",
                                                                            "PENDING_PAYMENT")
                                                                    .contains(o.status()))
                                            .count();
                            return new ProviderBuyerCompany(
                                    c.id(),
                                    c.name(),
                                    org,
                                    assets.size(),
                                    assets.stream().filter(this::isCritical).count(),
                                    active,
                                    history.size(),
                                    org == null
                                            ? List.of()
                                            : sites.findByOrganizationId(org).stream()
                                                    .filter(
                                                            s ->
                                                                    org.equals(
                                                                                    s
                                                                                            .getOrganizationId())
                                                                            && s.isActive())
                                                    .sorted(Comparator.comparing(s -> s.getId()))
                                                    .map(
                                                            s ->
                                                                    new ProviderBuyerCompany.Site(
                                                                            s.getId(),
                                                                            s
                                                                                    .getCustomerAccountId(),
                                                                            s.getName(),
                                                                            s.getAddress()))
                                                    .toList());
                        })
                .toList();
    }

    public boolean canReadTank(Long providerId, Long tankId) {
        if (providerId == null || tankId == null) return false;
        return tanks.findById(tankId)
                .map(t -> linkedOrganizations(providerId).contains(t.getOrganizationId()))
                .orElse(false);
    }

    public Set<Long> linkedOrganizations(Long providerId) {
        var linked = new HashSet<Long>();
        links.findByProviderId(providerId).stream()
                .map(l -> l.getOrganizationId())
                .forEach(linked::add);
        requests.findByProviderId(providerId).stream()
                .filter(r -> providerId.equals(r.providerId()))
                .map(ReplenishmentLookup.ReplenishmentView::organizationId)
                .filter(Objects::nonNull)
                .forEach(linked::add);
        orders.findByProviderId(providerId).stream()
                .filter(o -> providerId.equals(o.providerId()))
                .map(o -> companies.findById(o.companyId()))
                .flatMap(Optional::stream)
                .map(BuyerCompanyDirectory.BuyerSnapshot::organizationId)
                .filter(Objects::nonNull)
                .forEach(linked::add);
        return linked;
    }

    public Optional<Long> linkedBuyerOrganization(Long providerId, Long buyerCompanyId) {
        var explicit = links.findByProviderIdAndBuyerCompanyId(providerId, buyerCompanyId);
        if (explicit.isPresent()) return Optional.of(explicit.get().getOrganizationId());
        return companies
                .findById(buyerCompanyId)
                .map(BuyerCompanyDirectory.BuyerSnapshot::organizationId)
                .filter(org -> linkedOrganizations(providerId).contains(org));
    }

    @Override
    public Optional<Long> linkedBuyerCompany(Long providerId, Long organizationId) {
        return links.findByProviderId(providerId).stream()
                .filter(l -> organizationId.equals(l.getOrganizationId()))
                .map(l -> l.getBuyerCompanyId())
                .findFirst()
                .or(() -> legacy.buyerCompanyIdForOrganization(organizationId));
    }

    private boolean isCritical(Tank tank) {
        double threshold =
                policies.findPolicy(tank.getId())
                        .filter(p -> tank.getOrganizationId().equals(p.organizationId()))
                        .map(TankRefillLookup.PolicySnapshot::lowLevelPercent)
                        .orElse(policies.defaultLowLevelPercent());
        return 100.0
                        * tank.getCurrentLevel().convertedTo(tank.getCapacity().unit()).amount()
                        / tank.getCapacity().amount()
                <= threshold;
    }
}
