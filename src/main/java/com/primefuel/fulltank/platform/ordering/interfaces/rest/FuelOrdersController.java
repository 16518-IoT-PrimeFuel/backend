package com.primefuel.fulltank.platform.ordering.interfaces.rest;

import com.primefuel.fulltank.platform.ordering.application.commandservices.FuelOrderCommandService;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.CancelFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.commands.ConfirmFuelOrderCommand;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetAllFuelOrdersQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByCompanyIdQuery;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrdersByProviderIdQuery;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.CreateFuelOrderResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.resources.FuelOrderResource;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.CreateFuelOrderCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.ordering.interfaces.rest.transform.FuelOrderResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
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
@RequestMapping(value = "/api/v1/fuel-orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Fuel Orders", description = "Ordering management endpoints")
public class FuelOrdersController {

    private final FuelOrderCommandService fuelOrderCommandService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final CurrentUserAccess currentUserAccess;

    public FuelOrdersController(FuelOrderCommandService fuelOrderCommandService,
                                FuelOrderQueryService fuelOrderQueryService,
                                CurrentUserAccess currentUserAccess) {
        this.fuelOrderCommandService = fuelOrderCommandService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Creates a fuel order for the caller's buyer company.
     *
     * <p>The company in the body must be the caller's own, the fuel product must belong to the
     * requested provider, and any referenced equipment must belong to the same company; those
     * cross-tenant checks are the ones added by the R01 hotfix.</p>
     */
    @Operation(summary = "Create a fuel order",
            description = "Creates a fuel order for the caller's company, validating provider/product and company/equipment ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fuel order created."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the company, or the product/equipment does not belong to the referenced provider/company."),
            @ApiResponse(responseCode = "404", description = "The referenced fuel product or equipment does not exist.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> createFuelOrder(@RequestBody CreateFuelOrderResource resource) {
        var command = CreateFuelOrderCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = fuelOrderCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Confirms a fuel order.
     *
     * <p>Only the buyer company that owns the order may confirm it. The transition is not guarded by
     * order state, so it is accepted from any current status.</p>
     */
    @Operation(summary = "Confirm a fuel order",
            description = "Moves the given fuel order to the confirmed status on behalf of its owning buyer company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel order confirmed."),
            @ApiResponse(responseCode = "404", description = "Fuel order does not exist or belongs to another buyer company.")
    })
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long orderId) {
        if (!ownsOrderAsBuyer(orderId)) return ResponseEntity.notFound().build();
        var result = fuelOrderCommandService.handle(new ConfirmFuelOrderCommand(orderId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Cancels a fuel order.
     *
     * <p>Either the buyer company or the provider tenant of the order may cancel it. The transition is
     * not guarded by order state, so it is accepted from any current status.</p>
     */
    @Operation(summary = "Cancel a fuel order",
            description = "Moves the given fuel order to the cancelled status on behalf of its buyer company or provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel order cancelled."),
            @ApiResponse(responseCode = "404", description = "Fuel order does not exist or is not owned by the caller.")
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        if (!ownsOrder(orderId)) return ResponseEntity.notFound().build();
        var result = fuelOrderCommandService.handle(new CancelFuelOrderCommand(orderId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                FuelOrderResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lists every fuel order in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all fuel orders",
            description = "Returns every registered fuel order. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel orders returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<FuelOrderResource>> getAllOrders() {
        var orders = fuelOrderQueryService.handle(new GetAllFuelOrdersQuery());
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single fuel order.
     *
     * <p>Readable by the buyer company or the provider tenant of the order; anything else is reported
     * as not found.</p>
     */
    @Operation(summary = "Get a fuel order by id",
            description = "Returns the fuel order identified by the path id when the caller is its buyer company or provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel order returned."),
            @ApiResponse(responseCode = "404", description = "Fuel order does not exist or is not owned by the caller.")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<FuelOrderResource> getOrderById(@PathVariable Long orderId) {
        var result = fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompanyOrProvider(order.getCompanyId(), order.getProviderId()));
        return result.map(o -> new ResponseEntity<>(
                        FuelOrderResourceFromEntityAssembler.toResourceFromEntity(o), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lists the fuel orders of a buyer company.
     *
     * <p>Only the owning company may list them.</p>
     */
    @Operation(summary = "List fuel orders by company",
            description = "Returns all fuel orders of the given buyer company when it matches the caller's own company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel orders returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested company.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<FuelOrderResource>> getOrdersByCompany(@PathVariable Long companyId) {
        var orders = fuelOrderQueryService.handle(new GetFuelOrdersByCompanyIdQuery(companyId));
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Lists the fuel orders of a provider tenant.
     *
     * <p>Only the owning provider tenant may list them.</p>
     */
    @Operation(summary = "List fuel orders by provider",
            description = "Returns all fuel orders of the given provider when it matches the caller's own provider tenant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fuel orders returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested provider tenant.")
    })
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    public ResponseEntity<List<FuelOrderResource>> getOrdersByProvider(@PathVariable Long providerId) {
        var orders = fuelOrderQueryService.handle(new GetFuelOrdersByProviderIdQuery(providerId));
        var resources = orders.stream().map(FuelOrderResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private boolean ownsOrder(Long orderId) {
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompanyOrProvider(
                        order.getCompanyId(), order.getProviderId()))
                .isPresent();
    }

    private boolean ownsOrderAsBuyer(Long orderId) {
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .filter(order -> currentUserAccess.ownsCompany(order.getCompanyId()))
                .isPresent();
    }
}
