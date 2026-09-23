package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.commandservices.BuyerCompanyCommandService;
import com.primefuel.fulltank.platform.iam.application.queryservices.BuyerCompanyQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetAllBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetBuyerCompanyByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.BuyerCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.CreateBuyerCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.BuyerCompanyResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.CreateBuyerCompanyCommandFromResourceAssembler;
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
@RequestMapping(value = "/api/v1/buyer-companies", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Buyer Companies", description = "Buyer company management endpoints")
public class BuyerCompaniesController {

    private final BuyerCompanyCommandService buyerCompanyCommandService;
    private final BuyerCompanyQueryService buyerCompanyQueryService;
    private final BuyerCompanyRepository buyerCompanyRepository;

    public BuyerCompaniesController(BuyerCompanyCommandService buyerCompanyCommandService,
                                    BuyerCompanyQueryService buyerCompanyQueryService,
                                    BuyerCompanyRepository buyerCompanyRepository) {
        this.buyerCompanyCommandService = buyerCompanyCommandService;
        this.buyerCompanyQueryService = buyerCompanyQueryService;
        this.buyerCompanyRepository = buyerCompanyRepository;
    }

    /**
     * Creates a standalone buyer company profile.
     *
     * <p>Public endpoint (self-service): it does not require an authenticated caller and does not
     * create a user or membership. Any failure while persisting the profile is surfaced as a
     * server error rather than a validation error.</p>
     */
    @Operation(summary = "Create a buyer company",
            description = "Persists a new buyer company profile. This registration endpoint is open to unauthenticated callers.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Buyer company created."),
            @ApiResponse(responseCode = "500", description = "Unexpected error while persisting the buyer company.")
    })
    @PostMapping
    public ResponseEntity<?> createBuyerCompany(@RequestBody CreateBuyerCompanyResource resource) {
        var command = CreateBuyerCompanyCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = buyerCompanyCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                BuyerCompanyResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Lists every buyer company in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all buyer companies",
            description = "Returns every registered buyer company. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Buyer companies returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<BuyerCompanyResource>> getAllBuyerCompanies() {
        var companies = buyerCompanyQueryService.handle(new GetAllBuyerCompaniesQuery());
        var resources = companies.stream().map(BuyerCompanyResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single buyer company.
     *
     * <p>Only the buyer that owns the profile may read it.</p>
     */
    @Operation(summary = "Get a buyer company by id",
            description = "Returns the buyer company identified by the path id when the caller owns it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Buyer company returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner of this buyer company."),
            @ApiResponse(responseCode = "404", description = "Buyer company does not exist.")
    })
    @GetMapping("/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<BuyerCompanyResource> getBuyerCompanyById(@PathVariable Long companyId) {
        var result = buyerCompanyQueryService.handle(new GetBuyerCompanyByIdQuery(companyId));
        return result.map(company -> new ResponseEntity<>(
                        BuyerCompanyResourceFromEntityAssembler.toResourceFromEntity(company), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Updates a buyer company profile in place.
     *
     * <p>Only the owning buyer may update the profile. Fields are overwritten directly through the
     * repository without running the command pipeline.</p>
     */
    @Operation(summary = "Update a buyer company",
            description = "Replaces the editable fields of the buyer company identified by the path id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Buyer company updated."),
            @ApiResponse(responseCode = "403", description = "Caller is not the owner of this buyer company."),
            @ApiResponse(responseCode = "404", description = "Buyer company does not exist.")
    })
    @PutMapping("/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<BuyerCompanyResource> updateBuyerCompany(@PathVariable Long companyId,
                                                                   @RequestBody CreateBuyerCompanyResource resource) {
        var result = buyerCompanyRepository.findById(companyId);
        if (result.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        var company = result.get();
        company.setName(resource.name());
        company.setRuc(resource.ruc());
        company.setSector(resource.sector());
        company.setAddress(resource.address());
        company.setContactEmail(resource.contactEmail());
        company.setPhone(resource.phone());
        var updated = buyerCompanyRepository.save(company);
        return new ResponseEntity<>(BuyerCompanyResourceFromEntityAssembler.toResourceFromEntity(updated), HttpStatus.OK);
    }
}
