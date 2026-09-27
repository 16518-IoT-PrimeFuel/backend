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
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import com.primefuel.fulltank.platform.ordering.api.OrderLookup;
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
@Tag(name = "Pagos", description = "Registro, consulta y gestión del estado de pagos de órdenes")
public class PaymentsController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final OrderLookup orderLookup;
    private final TenantAccess tenantAccess;

    public PaymentsController(PaymentCommandService paymentCommandService,
                              PaymentQueryService paymentQueryService,
                              OrderLookup orderLookup,
                              TenantAccess tenantAccess) {
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
        this.orderLookup = orderLookup;
        this.tenantAccess = tenantAccess;
    }

    /**
     * Registra un pago para una orden de la empresa compradora autenticada.
     *
     * <p>La empresa del cuerpo debe pertenecer al usuario y coincidir con la de la orden. El importe debe
     * igualar el total de la orden y solo se permite un pago por orden.</p>
     */
    @Operation(summary = "Registrar pago",
            description = "Registra el pago de una orden después de verificar la empresa propietaria, la existencia de la orden y la coincidencia del importe total.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado."),
            @ApiResponse(responseCode = "400", description = "Falta la orden o la empresa, o el importe no coincide con el total de la orden."),
            @ApiResponse(responseCode = "403", description = "La empresa compradora indicada no pertenece al usuario autenticado."),
            @ApiResponse(responseCode = "404", description = "La orden no existe o no pertenece a la empresa indicada."),
            @ApiResponse(responseCode = "409", description = "La orden ya tiene un pago registrado.")
    })
    @PostMapping
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentResource resource) {
        // T23-B (fixes T23-A finding F1): the presence checks run BEFORE authorization, so a null
        // order/company answers the documented 400 instead of the previous 403; ownership of the declared
        // company is then enforced explicitly (403). The remaining invariants (order exists + belongs to the
        // company, amount positive and matching the order snapshot, one payment per order) live in the
        // application layer (PaymentCommandServiceImpl).
        if (resource.orderId() == null || resource.companyId() == null) {
            return ResponseEntity.badRequest().body("Order and buyer company are required");
        }
        if (!tenantAccess.ownsCompany(resource.companyId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var command = CreatePaymentCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = paymentCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Completa un pago y marca la orden como pagada.
     *
     * <p>Puede operarlo la empresa compradora o el distribuidor de la orden. La orden cancelada no admite
     * el pago; esa condición actualmente se informa como error interno.</p>
     */
    @Operation(summary = "Completar pago",
            description = "Marca el pago como completado con la referencia de transacción y actualiza la orden a pagada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago completado y orden marcada como pagada."),
            @ApiResponse(responseCode = "404", description = "El pago o su orden no existe, o el usuario no puede consultarlo."),
            @ApiResponse(responseCode = "500", description = "La orden no puede marcarse como pagada, por ejemplo, porque fue cancelada.")
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
     * Reembolsa un pago.
     *
     * <p>Puede operarlo la empresa compradora o el distribuidor de la orden. El estado pasa a reembolsado sin restricción del estado previo.</p>
     */
    @Operation(summary = "Reembolsar pago",
            description = "Marca el pago como reembolsado; repetir la operación conserva ese estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago reembolsado."),
            @ApiResponse(responseCode = "404", description = "El pago no existe o el usuario no puede consultarlo.")
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
     * Lista todos los pagos de la plataforma.
     *
     * <p>Disponible únicamente para usuarios con autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar todos los pagos",
            description = "Devuelve todos los pagos registrados; requiere autoridad administrativa ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de pagos."),
            @ApiResponse(responseCode = "403", description = "El usuario no cuenta con autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<PaymentResource>> getAllPayments() {
        var payments = paymentQueryService.handle(new GetAllPaymentsQuery());
        var resources = payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta un pago por identificador.
     *
     * <p>Visible para la empresa compradora o el distribuidor de la orden; los demás usuarios reciben una respuesta de no encontrado.</p>
     */
    @Operation(summary = "Consultar pago por identificador",
            description = "Devuelve el pago si el usuario pertenece a la empresa compradora o al distribuidor de la orden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago devuelto."),
            @ApiResponse(responseCode = "404", description = "El pago no existe o no es visible para el usuario.")
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResource> getPaymentById(@PathVariable Long paymentId) {
        var result = paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                .filter(payment -> tenantAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(payment.getOrderId()));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Consulta el pago asociado a una orden.
     *
     * <p>Visible para la empresa compradora o el distribuidor de la orden; los demás usuarios reciben una respuesta de no encontrado.</p>
     */
    @Operation(summary = "Consultar pago de una orden",
            description = "Devuelve el pago asociado a la orden si el usuario pertenece a la empresa compradora o al distribuidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago devuelto."),
            @ApiResponse(responseCode = "404", description = "La orden no tiene pago o este no es visible para el usuario.")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResource> getPaymentByOrder(@PathVariable Long orderId) {
        var result = paymentQueryService.handle(new GetPaymentByOrderIdQuery(orderId))
                .filter(payment -> tenantAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(orderId));
        return result.map(p -> new ResponseEntity<>(
                        PaymentResourceFromEntityAssembler.toResourceFromEntity(p), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Lista los pagos de una empresa compradora.
     *
     * <p>Solo el tenant de la empresa propietaria puede consultarlos.</p>
     */
    @Operation(summary = "Listar pagos por empresa",
            description = "Devuelve los pagos de la empresa indicada si coincide con la empresa del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de pagos de la empresa."),
            @ApiResponse(responseCode = "403", description = "La empresa solicitada no pertenece al usuario autenticado.")
    })
    @GetMapping("/company/{companyId}")
    @PreAuthorize("@tenantAccess.ownsCompany(#companyId)")
    public ResponseEntity<List<PaymentResource>> getPaymentsByCompany(@PathVariable Long companyId) {
        var payments = paymentQueryService.handle(new GetPaymentsByCompanyIdQuery(companyId));
        var resources = payments.stream().map(PaymentResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    private boolean ownsPayment(Long paymentId) {
        return paymentQueryService.handle(new GetPaymentByIdQuery(paymentId))
                .filter(payment -> tenantAccess.ownsCompany(payment.getCompanyId())
                        || ownsOrderAsProvider(payment.getOrderId()))
                .isPresent();
    }

    private boolean ownsOrderAsProvider(Long orderId) {
        return orderLookup.findById(orderId)
                .map(order -> tenantAccess.ownsProvider(order.providerId()))
                .orElse(false);
    }
}
