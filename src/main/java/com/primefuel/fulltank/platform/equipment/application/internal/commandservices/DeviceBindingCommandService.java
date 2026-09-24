package com.primefuel.fulltank.platform.equipment.application.internal.commandservices;

import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingData;
import com.primefuel.fulltank.platform.equipment.application.ports.DeviceBindingStore;
import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import com.primefuel.fulltank.platform.shared.application.events.DurableEvent;
import com.primefuel.fulltank.platform.shared.application.events.DurableEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class DeviceBindingCommandService {
    private final CustomerSiteCommandService sites;
    private final TankStore tanks;
    private final DeviceBindingStore bindings;
    private final DurableEventPublisher events;

    public DeviceBindingCommandService(CustomerSiteCommandService sites, TankStore tanks,
                                       DeviceBindingStore bindings, DurableEventPublisher events) {
        this.sites = sites;
        this.tanks = tanks;
        this.bindings = bindings;
        this.events = events;
    }

    @Transactional
    public DeviceBindingData bind(Long companyId, Long siteId, Long tankId, String deviceId, String channel,
                                  String credential, Instant validFrom, Instant validUntil) {
        requireText(deviceId, "deviceId");
        requireText(channel, "channel");
        requireText(credential, "credential");
        if (!sites.siteBelongsToCompany(siteId, companyId) || !tanks.belongsToSite(tankId, siteId)) {
            throw new IllegalArgumentException("Tank does not belong to buyer company");
        }
        var start = validFrom == null ? Instant.now() : validFrom;
        if (validUntil != null && !validUntil.isAfter(start)) {
            throw new IllegalArgumentException("validUntil must be after validFrom");
        }
        var overlaps = bindings.findByDeviceAndChannel(deviceId, channel).stream()
                .filter(existing -> existing.status().equals("ACTIVE") || existing.validUntil() == null
                        || existing.validUntil().isAfter(start))
                .anyMatch(existing -> overlaps(existing.validFrom(), existing.validUntil(), start, validUntil));
        if (overlaps) throw new IllegalArgumentException("Device binding overlaps an existing interval");

        var saved = bindings.save(new DeviceBindingData(null, deviceId, channel, tankId, companyId,
                hash(credential), start, validUntil, "ACTIVE"));
        events.publish(new DurableEvent("device-bound:" + saved.id(), "DeviceBound", "DeviceBinding",
                saved.id().toString(), "deviceId=" + deviceId + ";tankId=" + tankId, Instant.now()));
        return saved;
    }

    @Transactional
    public void revoke(Long companyId, Long bindingId) {
        var binding = bindings.findById(bindingId).orElseThrow(() -> new IllegalArgumentException("Binding not found"));
        if (!companyId.equals(binding.buyerCompanyId())) throw new IllegalArgumentException("Binding is outside tenant");
        var now = Instant.now();
        bindings.revoke(bindingId, now);
        events.publish(new DurableEvent("device-revoked:" + bindingId, "DeviceRevoked", "DeviceBinding",
                bindingId.toString(), "deviceId=" + binding.deviceId(), now));
    }

    @Transactional
    public DeviceBindingData move(Long companyId, Long siteId, Long tankId, Long bindingId,
                                  Instant validFrom) {
        var current = bindings.findById(bindingId).orElseThrow(() -> new IllegalArgumentException("Binding not found"));
        if (!companyId.equals(current.buyerCompanyId())) throw new IllegalArgumentException("Binding is outside tenant");
        if (!sites.siteBelongsToCompany(siteId, companyId) || !tanks.belongsToSite(tankId, siteId)) {
            throw new IllegalArgumentException("Tank does not belong to buyer company");
        }
        var start = validFrom == null ? Instant.now() : validFrom;
        if (!start.isAfter(current.validFrom())) throw new IllegalArgumentException("Move must advance binding time");
        bindings.revoke(bindingId, start);
        var moved = bindings.save(new DeviceBindingData(null, current.deviceId(), current.channel(), tankId, companyId,
                current.credentialHash(), start, null, "ACTIVE"));
        events.publish(new DurableEvent("device-moved:" + moved.id(), "DeviceMoved", "DeviceBinding",
                moved.id().toString(), "deviceId=" + moved.deviceId() + ";tankId=" + tankId, Instant.now()));
        return moved;
    }

    public DeviceBindingData resolve(String deviceId, String channel, String credential, Instant capturedAt) {
        var at = capturedAt == null ? Instant.now() : capturedAt;
        return bindings.findByDeviceAndChannel(deviceId, channel).stream()
                .filter(binding -> !at.isBefore(binding.validFrom())
                        && (binding.validUntil() == null || at.isBefore(binding.validUntil())))
                .filter(binding -> binding.credentialHash().equals(hash(credential)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No valid device binding for reading"));
    }

    private static boolean overlaps(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
        var max = Instant.MAX;
        return leftStart.isBefore(rightEnd == null ? max : rightEnd)
                && rightStart.isBefore(leftEnd == null ? max : leftEnd);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static String hash(String credential) {
        requireText(credential, "credential");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(credential.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
