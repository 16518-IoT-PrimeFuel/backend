package com.primefuel.fulltank.platform.equipment.application.internal.queryservices;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.application.queryservices.ProviderTankQueryService;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.Tank;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetProviderTanksQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.ProviderTank;
import com.primefuel.fulltank.platform.equipment.domain.repositories.CustomerSiteRepository;
import com.primefuel.fulltank.platform.equipment.domain.repositories.TankRepository;
import com.primefuel.fulltank.platform.iam.api.BuyerCompanyDirectory;
import com.primefuel.fulltank.platform.iam.api.LegacyCompanyDirectory;
import com.primefuel.fulltank.platform.inventory.api.FuelProductLookup;
import com.primefuel.fulltank.platform.replenishment.api.TankRefillLookup;
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.application.result.Result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProviderTankQueryServiceImpl implements ProviderTankQueryService {
    private final ProviderBuyerAccess buyers;
    private final BuyerCompanyDirectory companies;
    private final LegacyCompanyDirectory legacy;
    private final TankRepository tanks;
    private final CustomerSiteRepository sites;
    private final DeviceBindingRepository devices;
    private final TankRefillLookup policies;
    private final FuelProductLookup products;

    public ProviderTankQueryServiceImpl(
            ProviderBuyerAccess buyers,
            BuyerCompanyDirectory companies,
            LegacyCompanyDirectory legacy,
            TankRepository tanks,
            CustomerSiteRepository sites,
            DeviceBindingRepository devices,
            TankRefillLookup policies,
            FuelProductLookup products) {
        this.buyers = buyers;
        this.companies = companies;
        this.legacy = legacy;
        this.tanks = tanks;
        this.sites = sites;
        this.devices = devices;
        this.policies = policies;
        this.products = products;
    }

    public Result<List<ProviderTank>, ApplicationError> handle(GetProviderTanksQuery query) {
        var linked = buyers.linkedOrganizations(query.providerId());
        var authorized = linked;
        if (query.buyerCompanyId() != null) {
            var organization =
                    buyers.linkedBuyerOrganization(query.providerId(), query.buyerCompanyId())
                            .orElse(null);
            if (organization == null)
                return Result.failure(
                        ApplicationError.notFound(
                                "BuyerCompany", query.buyerCompanyId().toString()));
            authorized = Set.of(organization);
        }
        var assets =
                authorized.stream()
                        .flatMap(
                                org ->
                                        tanks.findByOrganizationId(org).stream()
                                                .filter(
                                                        t ->
                                                                org.equals(t.getOrganizationId())
                                                                        && t.isActive()))
                        .sorted(Comparator.comparing(Tank::getId))
                        .map(t -> toSnapshot(t, query.providerId()))
                        .toList();
        return Result.success(assets);
    }

    private ProviderTank toSnapshot(Tank t, Long providerId) {
        var policy =
                policies.findPolicy(t.getId())
                        .filter(p -> t.getOrganizationId().equals(p.organizationId()));
        double low =
                policy.map(TankRefillLookup.PolicySnapshot::lowLevelPercent)
                        .orElse(policies.defaultLowLevelPercent());
        double percent =
                100
                        * t.getCurrentLevel().convertedTo(t.getCapacity().unit()).amount()
                        / t.getCapacity().amount();
        var site =
                sites.findById(t.getSiteId())
                        .filter(
                                s ->
                                        t.getOrganizationId().equals(s.getOrganizationId())
                                                && t.getCustomerAccountId()
                                                        .equals(s.getCustomerAccountId()))
                        .orElse(null);
        var deviceList =
                devices.findOpenByTankId(t.getId()).stream()
                        .filter(
                                d ->
                                        t.getOrganizationId().equals(d.getOrganizationId())
                                                && d.isOpen()
                                                && d.covers(Instant.now()))
                        .sorted(Comparator.comparing(d -> d.getId()))
                        .map(
                                d ->
                                        new ProviderTank.Device(
                                                d.getDeviceId(), d.getChannel(), d.getValidFrom()))
                        .toList();
        var productId =
                policy.map(TankRefillLookup.PolicySnapshot::fuelProductId)
                        .flatMap(products::findById)
                        .filter(p -> providerId.equals(p.providerId()))
                        .map(FuelProductLookup.ProductSnapshot::id)
                        .orElse(null);
        return new ProviderTank(
                t.getId(),
                buyers.linkedBuyerCompany(providerId, t.getOrganizationId()).orElse(null),
                t.getOrganizationId(),
                t.getCustomerAccountId(),
                t.getSiteId(),
                t.getName(),
                site == null ? null : site.getName(),
                site == null ? null : site.getAddress(),
                t.getFuelType(),
                productId,
                t.getCapacity().amount(),
                t.getCurrentLevel().amount(),
                t.getCapacity().unit().name(),
                percent,
                low,
                percent <= low,
                t.getLevelObservedAt(),
                t.getLevelSource(),
                deviceList);
    }
}
