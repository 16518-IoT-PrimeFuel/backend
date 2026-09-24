package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.internal.commandservices.DeviceBindingCommandService;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.BindDeviceResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.MoveDeviceResource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/sites/{siteId}/tanks/{tankId}/devices",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceBindingsController {
    private final DeviceBindingCommandService service;

    public DeviceBindingsController(DeviceBindingCommandService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<?> bind(@PathVariable Long companyId, @PathVariable Long siteId, @PathVariable Long tankId,
                                  @Valid @RequestBody BindDeviceResource resource) {
        try {
            var binding = service.bind(companyId, siteId, tankId, resource.deviceId(), resource.channel(),
                    resource.credential(), resource.validFrom(), resource.validUntil());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("bindingId", binding.id(), "status", binding.status()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @DeleteMapping("/{bindingId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<?> revoke(@PathVariable Long companyId, @PathVariable Long bindingId) {
        try {
            service.revoke(companyId, bindingId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping(value = "/{bindingId}/move", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<?> move(@PathVariable Long companyId, @PathVariable Long siteId, @PathVariable Long tankId,
                                  @PathVariable Long bindingId, @RequestBody MoveDeviceResource resource) {
        try {
            var binding = service.move(companyId, siteId, tankId, bindingId, resource.validFrom());
            return ResponseEntity.ok(Map.of("bindingId", binding.id(), "status", binding.status()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
