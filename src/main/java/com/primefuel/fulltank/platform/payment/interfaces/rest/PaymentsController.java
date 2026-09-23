package com.primefuel.fulltank.platform.payment.interfaces.rest;

import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.application.queryservices.PaymentQueryService;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.RefundPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetAllPaymentsQuery;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentByIdQuery;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentByOrderIdQuery;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentsByCompanyIdQuery;
import com.primefuel.fulltank.platform.payment.interfaces.rest.resources.CompletePaymentResource;
import com.primefuel.fulltank.platform.payment.interfaces.rest.resources.CreatePaymentResource;
import com.primefuel.fulltank.platform.payment.interfaces.rest.resources.PaymentResource;
import com.primefuel.fulltank.platform.payment.interfaces.rest.transform.CreatePaymentCommandFromResourceAssembler;
import com.primefuel.fulltank.platform.payment.interfaces.rest.transform.PaymentResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.infrastructure.authorization.sfs.services.CurrentUserAccess;
import com.primefuel.fulltank.platform.ordering.application.queryservices.FuelOrderQueryService;
import com.primefuel.fulltank.platform.ordering.domain.model.queries.GetFuelOrderByIdQuery;
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
@RequestMapping(value = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payments", description = "Payment management endpoints")
public class PaymentsController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final FuelOrderQueryService fuelOrderQueryService;
    private final CurrentUserAccess currentUserAccess;

    public PaymentsController(PaymentCommandService paymentCommandService,
                              PaymentQueryService paymentQueryService,
                              FuelOrderQueryService fuelOrderQueryService,
                              CurrentUserAccess currentUserAccess) {
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
        this.fuelOrderQueryService = fuelOrderQueryService;
        this.currentUserAccess = currentUserAccess;
    }

    /**
     * Creates a payment for an order on behalf of the caller's buyer company.
     *
     * <p>The company in the body must be the caller's own and must match the order's company; the amount
     * must equal the order total. At most one payment may exist per order.</p>
     */
    @Operation(summary = "Create a payment",
            description = "Creates a payment for the caller's order after checking ownership and that the amount matches the order total.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment created."),
            @ApiResponse(responseCode = "400", description = "The order/company is missing or the amount does not match the order total."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the buyer company in the request body."),
            @ApiResponse(responseCode = "404", description = "The referenced order does not exist or does not belong to the given company."),
            @ApiResponse(responseCode = "409", description = "A payment already exists for the order.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentResource resource) {
        if (resource.orderId() == null || resource.companyId() == null) {
            return ResponseEntity.badRequest().body("Order and buyer company are required");
        }
        var order = fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(resource.orderId()))
                .filter(found -> resource.companyId().equals(found.getCompanyId()));
        if (order.isEmpty()) return ResponseEntity.notFound().build();
        if (resource.amount() == null || !resource.amount().equals(order.get().getTotalPrice())) {
            return ResponseEntity.badRequest().body("Payment amount must match the order total");
        }
        var command = CreatePaymentCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = paymentCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Completes a payment and marks the order as paid.
     *
     * <p>Readable/operable by the buyer company or the provider of the order. Completing also moves the
     * order to paid, which the order aggregate refuses for a cancelled order — that refusal surfaces as an
     * unexpected error rather than a domain conflict.</p>
     */
    @Operation(summary = "Complete a payment",
            description = "Marks the payment as completed with a transaction reference and moves its order to paid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment completed and order marked paid."),
            @ApiResponse(responseCode = "404", description = "Payment or its order does not exist, or the caller is not allowed to see it."),
            @ApiResponse(responseCode = "500", description = "The order cannot be paid (for example, it was cancelled).")
    })
    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<?> completePayment(@PathVariable Long paymentId,
                                             @RequestBody CompletePaymentResource resource) {
        if (!ownsPayment(paymentId)) return ResponseEntity.notFound().build();
        var result = paymentCommandService.handle(new CompletePaymentCommand(paymentId, resource.transactionReference()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Refunds a payment.
     *
     * <p>Operable by the buyer company or the provider of the order. The status is set to refunded without
     * a state guard, so the transition is accepted from any current status.</p>
     */
    @Operation(summary = "Refund a payment",
            description = "Marks the payment as refunded; the operation is idempotent with respect to payment state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment refunded."),
            @ApiResponse(responseCode = "404", description = "Payment does not exist or the caller is not allowed to see it.")
    })
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<?> refundPayment(@PathVariable Long paymentId) {
        if (!ownsPayment(paymentId)) return ResponseEntity.notFound().build();
        var result = paymentCommandService.handle(new RefundPaymentCommand(paymentId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Lists every payment in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all payments",
            description = "Returns every registered payment. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payments returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PaymentResource>> getAllPayments() {
        var payments = paymentQueryService.handle(new GetAllPaymentsQuery());
        var resources = payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single payment.
     *
     * <p>Visible to the payment's buyer company or to the provider of its order; anything else is reported
     * as not found.</p>
     */
    @Operation(summary = "Get a payment by id",
            description = "Returns the payment identified by the path id when the caller is its buyer company or its order's provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment returned."),
            @ApiResponse(responseCode = "404", description = "Payment does not exist or is not visible to the caller.")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResource> getPaymentById(@PathVariable Long paymentId) {
        var result = paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                .filter(payment -> currentUserAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(payment.getOrderId()));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Retrieves the payment of an order.
     *
     * <p>Visible to the payment's buyer company or to the provider of the order; anything else is reported
     * as not found.</p>
     */
    @Operation(summary = "Get the payment of an order",
            description = "Returns the payment attached to the given order when the caller is its buyer company or the order's provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment returned."),
            @ApiResponse(responseCode = "404", description = "No payment exists for the order or it is not visible to the caller.")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResource> getPaymentByOrder(@PathVariable Long orderId) {
        var result = paymentQueryService.handle(new GetPaymentByOrderIdQuery(orderId))
                .filter(payment -> currentUserAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(orderId));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lists the payments of a buyer company.
     *
     * <p>Only the owning company may list them.</p>
     */
    @Operation(summary = "List payments by company",
            description = "Returns the payments of the given buyer company when it matches the caller's own company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payments returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the requested company.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<PaymentResource>> getPaymentsByCompany(@PathVariable Long companyId) {
        var payments = paymentQueryService.handle(new GetPaymentsByCompanyIdQuery(companyId));
        var resources = payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private boolean ownsPayment(Long paymentId) {
        return paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                .filter(payment -> currentUserAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(payment.getOrderId()))
                .isPresent();
    }

    private boolean ownsOrderAsProvider(Long orderId) {
        return fuelOrderQueryService.handle(new GetFuelOrderByIdQuery(orderId))
                .map(order -> currentUserAccess.ownsProvider(order.getProviderId()))
                .orElse(false);
    }
}
