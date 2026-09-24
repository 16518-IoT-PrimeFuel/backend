package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.ports.*;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import com.primefuel.fulltank.platform.ordering.application.ports.AutomaticReplenishmentRequest;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CreateFuelRequestCommand;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
public class RefillPolicyCommandService {
    private final CustomerSiteCommandService sites;
    private final TankStore tanks;
    private final RefillPolicyStore policies;
    private final AutomaticReplenishmentRequest requests;
    private final DurableEventPublisher events;

    public RefillPolicyCommandService(CustomerSiteCommandService sites, TankStore tanks, RefillPolicyStore policies,
                                      AutomaticReplenishmentRequest requests, DurableEventPublisher events) {
        this.sites = sites;
        this.tanks = tanks;
        this.policies = policies;
        this.requests = requests;
        this.events = events;
    }

    @Transactional
    public RefillPolicyData configure(Long companyId, Long siteId, Long tankId, Long providerId, Long fuelProductId,
                                      double threshold, double hysteresis, double target, String address,
                                      boolean enabled) {
        if (!sites.siteBelongsToCompany(siteId, companyId) || !tanks.belongsToSite(tankId, siteId)) {
            throw new IllegalArgumentException("Tank does not belong to buyer company");
        }
        var capacity = tanks.capacity(tankId).orElseThrow(() -> new IllegalArgumentException("Tank not found"));
        if (!Double.isFinite(threshold) || !Double.isFinite(hysteresis) || !Double.isFinite(target)
                || threshold < 0 || hysteresis < 0 || target <= threshold || target > capacity
                || providerId == null || fuelProductId == null || address == null || address.isBlank()) {
            throw new IllegalArgumentException("Invalid refill policy");
        }
        return policies.savePolicy(new RefillPolicyData(tankId, companyId, providerId, fuelProductId,
                threshold, hysteresis, target, address, enabled));
    }

    @Transactional
    public void evaluate(Long tankId, double level, String quality, Instant capturedAt) {
        var policy = policies.findPolicy(tankId).filter(RefillPolicyData::enabled).orElse(null);
        if (policy == null || !Double.isFinite(level) || quality == null || !quality.equals("GOOD")) return;
        var activeEpisode = policies.findOpenEpisode(tankId);
        if (level > policy.thresholdValue() + policy.hysteresisValue()) {
            activeEpisode.ifPresent(episode -> policies.saveEpisode(new RefillEpisodeData(episode.episodeKey(), tankId,
                    episode.startedAt(), capturedAt, "CLOSED", episode.requestId())));
            return;
        }
        if (activeEpisode.isPresent()) return;
        var key = "tank:" + tankId + ":episode:" + capturedAt.toEpochMilli();
        var episode = policies.saveEpisode(new RefillEpisodeData(key, tankId, capturedAt, null, "OPEN", null));
        var request = requests.create(new CreateFuelRequestCommand(policy.buyerCompanyId(), policy.providerId(), tankId,
                policy.fuelProductId(), policy.targetVolume(), null, policy.deliveryAddress(), LocalDate.now().plusDays(1), "AUTOMATIC"));
        policies.saveEpisode(new RefillEpisodeData(episode.episodeKey(), tankId, capturedAt, Instant.now(), "REQUESTED", request.id()));
        events.publish(new DurableEvent("low-level:" + key, "LowLevelThresholdReached", "Tank", tankId.toString(),
                "episodeKey=" + key + ";requestId=" + request.id(), Instant.now()));
    }

}
