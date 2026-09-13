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

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<?> refundPayment(@PathVariable Long paymentId) {
        if (!ownsPayment(paymentId)) return ResponseEntity.notFound().build();
        var result = paymentCommandService.handle(new RefundPaymentCommand(paymentId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PaymentResource>> getAllPayments() {
        var payments = paymentQueryService.handle(new GetAllPaymentsQuery());
        var resources = payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResource> getPaymentById(@PathVariable Long paymentId) {
        var result = paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                .filter(payment -> currentUserAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(payment.getOrderId()));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResource> getPaymentByOrder(@PathVariable Long orderId) {
        var result = paymentQueryService.handle(new GetPaymentByOrderIdQuery(orderId))
                .filter(payment -> currentUserAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(orderId));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

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
