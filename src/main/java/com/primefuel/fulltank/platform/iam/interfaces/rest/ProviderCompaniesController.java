package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.commandservices.ProviderCompanyCommandService;
import com.primefuel.fulltank.platform.iam.application.queryservices.ProviderCompanyQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetAllProviderCompaniesQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetProviderCompanyByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.repositories.ProviderCompanyRepository;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.CreateProviderCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.ProviderCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.CreateProviderCompanyCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.ProviderCompanyResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/provider-companies", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Provider Companies", description = "Provider company management endpoints")
public class ProviderCompaniesController {

    private final ProviderCompanyCommandService providerCompanyCommandService;
    private final ProviderCompanyQueryService providerCompanyQueryService;
    private final ProviderCompanyRepository providerCompanyRepository;

    public ProviderCompaniesController(ProviderCompanyCommandService providerCompanyCommandService,
                                       ProviderCompanyQueryService providerCompanyQueryService,
                                       ProviderCompanyRepository providerCompanyRepository) {
        this.providerCompanyCommandService = providerCompanyCommandService;
        this.providerCompanyQueryService = providerCompanyQueryService;
        this.providerCompanyRepository = providerCompanyRepository;
    }

    /**
     * Creates a standalone provider company profile.
     *
     * <p>Public endpoint (self-service): it does not require an authenticated caller and does not
     * create a user or membership. A persistence failure is surfaced as a server error instead of a
     * validation error.</p>
     */
    @Operation(summary = "Create a provider company",
            description = "Persists a new provider company profile. This registration endpoint is open to unauthenticated callers.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Provider company created."),
            @ApiResponse(responseCode = "500", description = "Unexpected error while persisting the provider company.")
    })
    @PostMapping
    public ResponseEntity<?> createProviderCompany(@RequestBody CreateProviderCompanyResource resource) {
        var command = CreateProviderCompanyCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = providerCompanyCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                ProviderCompanyResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Lists every provider company in the platform.
     *
     * <p>Visible to buyers (so they can pick a supplier) and to providers themselves.</p>
     */
    @Operation(summary = "List all provider companies",
            description = "Returns every registered provider company. Available to buyer and provider roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider companies returned."),
            @ApiResponse(responseCode = "403", description = "Caller holds neither the buyer nor the provider role.")
    })
    @GetMapping
    @PreAuthorize("@currentUserAccess.isBuyerRole()")
    public ResponseEntity<List<ProviderCompanyResource>> getAllProviderCompanies() {
        var companies = providerCompanyQueryService.handle(new GetAllProviderCompaniesQuery());
        var resources = companies.stream().map(ProviderCompanyResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single provider company.
     *
     * <p>Readable by any buyer, or by the provider that owns the profile.</p>
     */
    @Operation(summary = "Get a provider company by id",
            description = "Returns the provider company identified by the path id to a buyer or its owning provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider company returned."),
            @ApiResponse(responseCode = "403", description = "Caller is neither a buyer nor the owner of this provider company."),
            @ApiResponse(responseCode = "404", description = "Provider company does not exist.")
    })
    @GetMapping("/{providerId}")
    @PreAuthorize("@currentUserAccess.isBuyerRole() or @currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<ProviderCompanyResource> getProviderCompanyById(@PathVariable Long providerId) {
        var result = providerCompanyQueryService.handle(new GetProviderCompanyByIdQuery(providerId));
        return result.map(company -> new ResponseEntity<>(
                        ProviderCompanyResourceFromEntityAssembler.toResourceFromEntity(company), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Updates a provider company profile in place.
     *
     * <p>Only the owning provider may update the profile. Fields are overwritten directly through the
     * repository without running the command pipeline.</p>
     */
    @Operation(summary = "Update a provider company",
            description = "Replaces the editable fields of the provider company identified by the path id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider company updated."),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner of this provider company."),
            @ApiResponse(responseCode = "404", description = "Provider company does not exist.")
    })
    @PutMapping("/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<ProviderCompanyResource> updateProviderCompany(@PathVariable Long providerId,
                                                                         @RequestBody CreateProviderCompanyResource resource) {
        var result = providerCompanyRepository.findById(providerId);
        if (result.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        var provider = result.get();
        provider.setName(resource.name());
        provider.setRuc(resource.ruc());
        provider.setRating(resource.rating());
        provider.setAddress(resource.address());
        provider.setPhone(resource.phone());
        provider.setFuelTypesOffered(resource.fuelTypesOffered());
        provider.setDescription(resource.description());
        var updated = providerCompanyRepository.save(provider);
        return new ResponseEntity<>(ProviderCompanyResourceFromEntityAssembler.toResourceFromEntity(updated), HttpStatus.OK);
    }
}
