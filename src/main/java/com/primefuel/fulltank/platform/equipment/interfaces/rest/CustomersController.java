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
@Tag(name = "Clientes", description = "Cuentas de cliente y sus sitios de entrega en una organización")
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
     * Registra una cuenta de cliente en la organización activa del usuario.
     *
     * <p>La organización se obtiene de la identidad autenticada, no del cuerpo; el RUC, si se indica,
     * debe ser único dentro de esa organización.</p>
     */
    @Operation(summary = "Registrar una cuenta de cliente",
            description = "Crea la cuenta dentro de la organización activa del usuario. La organización se deriva de la membresía autenticada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta de cliente creada."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones requeridas."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con el mismo RUC en esta organización.")
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
     * Lista las cuentas de cliente de la organización activa.
     *
     * <p>La consulta usa la organización de la membresía autenticada y no acepta un tenant enviado por el cliente.</p>
     */
    @Operation(summary = "Listar cuentas de cliente",
            description = "Devuelve las cuentas asociadas a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuentas de cliente devueltas."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa.")
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
     * Registra un sitio de entrega en una cuenta de cliente de la organización activa.
     *
     * <p>La cuenta debe existir y pertenecer a la organización activa para impedir registros entre tenants.</p>
     */
    @Operation(summary = "Registrar un sitio de entrega",
            description = "Crea un sitio bajo la cuenta indicada, que debe pertenecer a la organización activa del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sitio creado."),
            @ApiResponse(responseCode = "400", description = "El cuerpo no cumple las validaciones requeridas."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado, no tiene organización activa o la cuenta pertenece a otro tenant."),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta de cliente indicada.")
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
     * Lista los sitios de una cuenta de cliente.
     *
     * <p>Una cuenta ajena o inexistente responde como no encontrada para no revelar identificadores de otros tenants.</p>
     */
    @Operation(summary = "Listar sitios de una cuenta",
            description = "Devuelve los sitios registrados bajo una cuenta que pertenece a la organización activa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sitios de entrega devueltos."),
            @ApiResponse(responseCode = "403", description = "El usuario no está autenticado o no tiene una organización activa."),
            @ApiResponse(responseCode = "404", description = "La cuenta no existe o pertenece a otra organización.")
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
