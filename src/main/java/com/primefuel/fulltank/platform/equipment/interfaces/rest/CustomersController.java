package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.application.commandservices.CustomerCommandService;
import com.primefuel.fulltank.platform.equipment.application.queryservices.CustomerQueryService;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterCustomerCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.commands.RegisterSiteCommand;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomerByIdQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetCustomersByOrganizationQuery;
import com.primefuel.fulltank.platform.equipment.domain.model.queries.GetSitesByCustomerQuery;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateCustomerResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CreateSiteResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.CustomerResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.SiteResource;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.CustomerResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.transform.SiteResourceFromDomainAssembler;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customers", description = "Customer accounts and sites (v2)")
public class CustomersController {

    private final CustomerCommandService customerCommandService;
    private final CustomerQueryService customerQueryService;
    private final MembershipAccess membershipAccess;

    public CustomersController(CustomerCommandService customerCommandService,
                               CustomerQueryService customerQueryService,
                               MembershipAccess membershipAccess) {
        this.customerCommandService = customerCommandService;
        this.customerQueryService = customerQueryService;
        this.membershipAccess = membershipAccess;
    }

    /**
     * Registers a customer account in the caller's own organization.
     *
     * <p>The owning organization is resolved from the principal, never from the body, so a caller
     * cannot register customers into a foreign tenant. The RUC, when supplied, must be unique within
     * that organization.</p>
     */
    @Operation(summary = "Register a customer account",
            description = "Creates a customer account owned by the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer account created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or no organization was resolved."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "409", description = "A customer with the same RUC already exists for this organization.")
    })
    @PostMapping
    public ResponseEntity<?> registerCustomer(@Valid @RequestBody CreateCustomerResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = customerCommandService.handle(new RegisterCustomerCommand(
                organizationId.get(), resource.name(), resource.ruc(), resource.address(),
                resource.contactEmail(), resource.phone(), resource.legacyCompanyId()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, CustomerResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Lists the customer accounts of the caller's organization.
     *
     * <p>Scoped to the organization derived from the principal; results never cross tenant
     * boundaries.</p>
     */
    @Operation(summary = "List customer accounts",
            description = "Returns the customer accounts belonging to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer accounts returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization.")
    })
    @GetMapping
    public ResponseEntity<List<CustomerResource>> listCustomers() {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var customers = customerQueryService.handle(new GetCustomersByOrganizationQuery(organizationId.get()));
        return new ResponseEntity<>(
                customers.stream().map(CustomerResourceFromDomainAssembler::toResourceFromDomain).toList(),
                HttpStatus.OK);
    }

    /**
     * Registers a delivery site under one of the caller's customer accounts.
     *
     * <p>The target customer must exist and belong to the caller's organization; otherwise the
     * service rejects the command (forbidden) rather than creating a cross-tenant site.</p>
     */
    @Operation(summary = "Register a site for a customer",
            description = "Creates a site under the given customer account, which must belong to the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Site created."),
            @ApiResponse(responseCode = "400", description = "Request body failed validation or no organization was resolved."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated, has no active organization, or the customer belongs to another organization."),
            @ApiResponse(responseCode = "404", description = "Customer account does not exist.")
    })
    @PostMapping("/{customerId}/sites")
    public ResponseEntity<?> registerSite(@PathVariable Long customerId,
                                          @Valid @RequestBody CreateSiteResource resource) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var result = customerCommandService.handle(new RegisterSiteCommand(
                organizationId.get(), customerId, resource.name(), resource.address()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, SiteResourceFromDomainAssembler::toResourceFromDomain, HttpStatus.CREATED);
    }

    /**
     * Lists the sites of a customer account.
     *
     * <p>A customer that does not exist or belongs to another organization is reported as not found,
     * so the endpoint never reveals foreign customer ids.</p>
     */
    @Operation(summary = "List sites of a customer",
            description = "Returns the sites registered under a customer account owned by the caller's organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sites returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not authenticated or has no active organization."),
            @ApiResponse(responseCode = "404", description = "Customer account does not exist or belongs to another organization.")
    })
    @GetMapping("/{customerId}/sites")
    public ResponseEntity<List<SiteResource>> listSites(@PathVariable Long customerId) {
        var organizationId = membershipAccess.currentOrganizationId();
        if (organizationId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var customer = customerQueryService.handle(new GetCustomerByIdQuery(customerId));
        if (customer.isEmpty() || !customer.get().getOrganizationId().equals(organizationId.get())) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        var sites = customerQueryService.handle(new GetSitesByCustomerQuery(customerId));
        return new ResponseEntity<>(
                sites.stream().map(SiteResourceFromDomainAssembler::toResourceFromDomain).toList(),
                HttpStatus.OK);
    }
}
