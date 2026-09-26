package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Vehicle;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.VehicleRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.VehicleResource;
import com.primefuel.fulltank.platform.iam.interfaces.acl.TenantAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehiclesController {

    private final VehicleRepository repository;
    private final TenantAccess currentUserAccess;

    public VehiclesController(VehicleRepository repository, TenantAccess currentUserAccess) {
        this.repository = repository;
        this.currentUserAccess = currentUserAccess;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public List<VehicleResource> getByProvider(@RequestParam Long providerId) {
        return repository.findByProviderId(providerId).stream().map(VehiclesController::toResource).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResource> getById(@PathVariable Long id) {
        return repository.findById(id)
                .filter(vehicle -> currentUserAccess.ownsProvider(vehicle.getProviderId()))
                .map(VehiclesController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<VehicleResource> create(@RequestBody VehicleResource resource) {
        var vehicle = new Vehicle(resource.providerId(), resource.licensePlate(), resource.brand(),
                resource.model(), resource.capacity(), defaultUnit(resource.unit()),
                defaultStatus(resource.status()));
        return new ResponseEntity<>(toResource(repository.save(vehicle)), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleResource> update(@PathVariable Long id, @RequestBody VehicleResource resource) {
        var vehicle = repository.findById(id).orElse(null);
        if (vehicle == null || !currentUserAccess.ownsProvider(vehicle.getProviderId())) {
            return ResponseEntity.notFound().build();
        }
        Long providerId = resource.providerId() != null ? resource.providerId() : vehicle.getProviderId();
        if (!currentUserAccess.ownsProvider(providerId)) return ResponseEntity.notFound().build();
        vehicle.update(providerId,
                resource.licensePlate(), resource.brand(), resource.model(), resource.capacity(),
                defaultUnit(resource.unit()), defaultStatus(resource.status()));
        return ResponseEntity.ok(toResource(repository.save(vehicle)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var vehicle = repository.findById(id).orElse(null);
        if (vehicle == null || !currentUserAccess.ownsProvider(vehicle.getProviderId())) {
            return ResponseEntity.notFound().build();
        }
        vehicle.setStatus("INACTIVE");
        vehicle.setEnabled(false);
        repository.save(vehicle);
        return ResponseEntity.noContent().build();
    }

    private static String defaultUnit(String unit) {
        return unit == null || unit.isBlank() ? "LITERS" : unit;
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "AVAILABLE" : status;
    }

    private static VehicleResource toResource(Vehicle vehicle) {
        return new VehicleResource(vehicle.getId(), vehicle.getProviderId(), vehicle.getLicensePlate(),
                vehicle.getBrand(), vehicle.getModel(), vehicle.getCapacity(), vehicle.getUnit(),
                vehicle.getStatus());
    }
}
