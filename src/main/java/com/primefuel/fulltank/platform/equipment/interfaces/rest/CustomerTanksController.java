package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.application.ports.TankStore;
import com.primefuel.fulltank.platform.equipment.application.ports.TankData;
import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateTankResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.TankStateResource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerTanksController {
    private final TankCommandService service;
    private final TankStore tanks;
    private final CustomerSiteCommandService sites;

    public CustomerTanksController(TankCommandService service, TankStore tanks, CustomerSiteCommandService sites) {
        this.service = service;
        this.tanks = tanks;
        this.sites = sites;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<?> list(@PathVariable Long companyId, @PathVariable Long siteId) {
        if (!sites.siteBelongsToCompany(siteId, companyId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(tanks.findBySiteId(siteId).stream().map(this::toResource).toList());
    }

    @GetMapping("/{tankId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<TankStateResource> get(@PathVariable Long companyId, @PathVariable Long siteId,
                                                 @PathVariable Long tankId) {
        if (!sites.siteBelongsToCompany(siteId, companyId)) return ResponseEntity.notFound().build();
        return tanks.findById(tankId)
                .filter(tank -> siteId.equals(tank.siteId()))
                .map(tank -> ResponseEntity.ok(toResource(tank)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long companyId,
                                                       @PathVariable Long siteId,
                                                       @Valid @RequestBody CreateTankResource resource) {
        try {
            var tankId = service.create(companyId, siteId, resource.name(), resource.fuelType(),
                    resource.capacity(), resource.unit(), resource.currentLevel());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("tankId", tankId, "status", "ACTIVE"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private TankStateResource toResource(TankData tank) {
        return new TankStateResource(tank.id(), tank.siteId(), tank.name(), tank.fuelType(), tank.capacity(),
                tank.unit(), tank.currentLevel(), tank.status(), tank.lastReadingAt());
    }
}
