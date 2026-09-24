package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.internal.commandservices.CustomerSiteCommandService;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.CreateCustomerSiteResource;
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
@RequestMapping(value = "/api/v2/buyer-companies/{companyId}/sites", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerSitesController {
    private final CustomerSiteCommandService service;

    public CustomerSitesController(CustomerSiteCommandService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long companyId,
                                                       @Valid @RequestBody CreateCustomerSiteResource resource) {
        try {
            var siteId = service.create(companyId, resource.name(), resource.address(), resource.sector());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("siteId", siteId, "status", "ACTIVE"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
