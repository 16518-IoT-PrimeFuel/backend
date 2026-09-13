package com.primefuel.fulltank.platform.fulfillment.interfaces.rest;

import com.primefuel.fulltank.platform.fulfillment.domain.model.aggregates.Driver;
import com.primefuel.fulltank.platform.fulfillment.domain.repositories.DriverRepository;
import com.primefuel.fulltank.platform.fulfillment.interfaces.rest.resources.DriverResource;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriversController {

    private final DriverRepository repository;
    private final CurrentUserAccess currentUserAccess;

    public DriversController(DriverRepository repository, CurrentUserAccess currentUserAccess) {
        this.repository = repository;
        this.currentUserAccess = currentUserAccess;
    }

    @GetMapping
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public List<DriverResource> getByProvider(@RequestParam Long providerId) {
        return repository.findByProviderId(providerId).stream().map(DriversController::toResource).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResource> getById(@PathVariable Long id) {
        return repository.findById(id)
                .filter(driver -> currentUserAccess.ownsProvider(driver.getProviderId()))
                .map(DriversController::toResource)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsProvider(#resource.providerId())")
    public ResponseEntity<DriverResource> create(@RequestBody DriverResource resource) {
        var driver = new Driver(resource.providerId(), resource.firstName(), resource.lastName(),
                resource.licenseNumber(), resource.phoneNumber(), resource.email(),
                defaultStatus(resource.status()));
        return new ResponseEntity<>(toResource(repository.save(driver)), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResource> update(@PathVariable Long id, @RequestBody DriverResource resource) {
        var driver = repository.findById(id).orElse(null);
        if (driver == null || !currentUserAccess.ownsProvider(driver.getProviderId())) {
            return ResponseEntity.notFound().build();
        }
        Long providerId = resource.providerId() != null ? resource.providerId() : driver.getProviderId();
        if (!currentUserAccess.ownsProvider(providerId)) return ResponseEntity.notFound().build();
        driver.update(providerId,
                resource.firstName(), resource.lastName(), resource.licenseNumber(),
                resource.phoneNumber(), resource.email(), defaultStatus(resource.status()));
        return ResponseEntity.ok(toResource(repository.save(driver)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var driver = repository.findById(id).orElse(null);
        if (driver == null || !currentUserAccess.ownsProvider(driver.getProviderId())) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String defaultStatus(String status) {
        return status == null || status.isBlank() ? "AVAILABLE" : status;
    }

    private static DriverResource toResource(Driver driver) {
        return new DriverResource(driver.getId(), driver.getProviderId(), driver.getFirstName(),
                driver.getLastName(), driver.getLicenseNumber(), driver.getPhoneNumber(),
                driver.getEmail(), driver.getStatus());
    }
}
