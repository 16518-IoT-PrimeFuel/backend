package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.RefillPolicyCommandService;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.ConfigureRefillPolicyResource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks/{tankId}/refill-policy",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class RefillPoliciesController {
    private final RefillPolicyCommandService service;

    public RefillPoliciesController(RefillPolicyCommandService service) {
        this.service = service;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<?> configure(@PathVariable Long companyId, @PathVariable Long siteId, @PathVariable Long tankId,
                                       @Valid @RequestBody ConfigureRefillPolicyResource resource) {
        try {
            var policy = service.configure(companyId, siteId, tankId, resource.providerId(), resource.fuelProductId(),
                    resource.threshold(), resource.hysteresis(), resource.targetVolume(), resource.deliveryAddress(), resource.enabled());
            return ResponseEntity.ok(Map.of("tankId", policy.tankId(), "enabled", policy.enabled()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
