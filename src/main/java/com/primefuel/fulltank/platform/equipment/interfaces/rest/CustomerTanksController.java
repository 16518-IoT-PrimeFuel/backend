package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.TankCommandService;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateTankResource;
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

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerTanksController {
    private final TankCommandService service;

    public CustomerTanksController(TankCommandService service) {
        this.service = service;
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
}
