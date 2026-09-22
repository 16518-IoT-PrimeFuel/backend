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
