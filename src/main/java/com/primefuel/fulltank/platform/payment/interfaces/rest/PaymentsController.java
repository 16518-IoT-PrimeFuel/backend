package com.primefuel.fulltank.platform.payment.interfaces.rest;

import org.springframework.web.server.ResponseStatusException;
import com.primefuel.fulltank.platform.payment.application.commandservices.PaymentCommandService;
import com.primefuel.fulltank.platform.payment.application.queryservices.PaymentQueryService;
import com.primefuel.fulltank.platform.payment.domain.model.commands.CompletePaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.FailPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.commands.RefundPaymentCommand;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetAllPaymentsQuery;
import com.primefuel.fulltank.platform.payment.domain.model.queries.GetPaymentsByProviderIdQuery;
import com.primefuel.fulltank.platform.payment.domain.model.valueobjects.PaymentStatus;
import com.primefuel.fulltank.platform.payment.interfaces.rest.resources.ProviderPaymentResource;
import com.primefuel.fulltank.platform.payment.interfaces.rest.transform.ProviderPaymentResourceFromDomainAssembler;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
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
import com.primefuel.fulltank.platform.shared.application.result.ApplicationError;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ErrorResponseAssembler;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

@RestController
@RequestMapping(value = "/api/payments", produces = MediaType.APPLICATION_JSON_VALUE)
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
     * <p>El importe se toma siempre del total de la orden (el {@code amount} del cuerpo se ignora) y la empresa
     * se deriva de la orden ({@code companyId} es opcional; si se envía debe ser la de la orden y del usuario).
     * La orden debe estar en PENDING_PAYMENT, solo se permite un pago vigente (no FAILED) por orden y el
     * {@code operationNumber} opcional (6 a 20 caracteres alfanuméricos) no puede repetirse.</p>
     */
    @Operation(summary = "Registrar pago",
            description = "Registra el pago de una orden en PENDING_PAYMENT por el total de la orden. companyId y amount se ignoran/derivan de la orden. operationNumber opcional: 6 a 20 caracteres alfanuméricos, único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado."),
            @ApiResponse(responseCode = "400", description = "Falta la orden, o el operationNumber no cumple 6-20 alfanuméricos."),
            @ApiResponse(responseCode = "403", description = "La empresa compradora indicada no pertenece al usuario autenticado."),
            @ApiResponse(responseCode = "404", description = "La orden no existe o no pertenece a la empresa indicada."),
            @ApiResponse(responseCode = "409", description = "La orden no está en PENDING_PAYMENT, ya tiene un pago vigente o el operationNumber ya existe.")
    })
    @PostMapping
    public ResponseEntity<?> createPayment(@RequestBody CreatePaymentResource resource) {
        // The presence check runs BEFORE authorization (400, not 403). The company is the order's own;
        // a client-sent companyId is only an optional assertion that must match and be owned by the user.
        if (resource.orderId() == null) {
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                    ApplicationError.validationError("payment", "Order is required"));
        }
        var companyId = resource.companyId() != null ? resource.companyId()
                : orderLookup.findById(resource.orderId()).map(OrderLookup.OrderSnapshot::companyId).orElse(null);
        if (companyId == null) {
            return ErrorResponseAssembler.toErrorResponseFromApplicationError(
                    ApplicationError.notFound("FuelOrder", resource.orderId().toString()));
        }
        if (!tenantAccess.ownsCompany(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var command = CreatePaymentCommandFromResourceAssembler.toCommandFromResource(
                new CreatePaymentResource(resource.orderId(), companyId, resource.amount(),
                        resource.paymentMethod(), resource.operationNumber()));
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
            @ApiResponse(responseCode = "409", description = "El pago no está PENDING (ya completado, fallido o reembolsado)."),
            @ApiResponse(responseCode = "500", description = "La orden no puede marcarse como pagada, por ejemplo, porque fue cancelada.")
    })
    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<?> completePayment(@PathVariable Long paymentId,
                                             @RequestBody CompletePaymentResource resource) {
        if (!ownsPayment(paymentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var result = paymentCommandService.handle(new CompletePaymentCommand(paymentId, resource.transactionReference()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Marca un pago PENDING como FAILED (rechazo o error de cobro); la orden queda libre para un nuevo pago.
     */
    @Operation(summary = "Marcar pago como fallido",
            description = "Pasa un pago PENDING a FAILED; la orden admite entonces un nuevo pago.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago marcado como FAILED."),
            @ApiResponse(responseCode = "404", description = "El pago no existe o el usuario no puede consultarlo."),
            @ApiResponse(responseCode = "409", description = "El pago no está PENDING.")
    })
    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<?> failPayment(@PathVariable Long paymentId) {
        if (!ownsPayment(paymentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var result = paymentCommandService.handle(new FailPaymentCommand(paymentId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                PaymentResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.OK);
    }

    /**
     * Reembolsa un pago.
     *
     * <p>Puede operarlo la empresa compradora o el distribuidor de la orden. Solo se reembolsa un pago COMPLETED; repetirlo sobre uno ya reembolsado es idempotente.</p>
     */
    @Operation(summary = "Reembolsar pago",
            description = "Marca como reembolsado un pago completado; repetir la operación conserva ese estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago reembolsado."),
            @ApiResponse(responseCode = "409", description = "El pago no está completado (PENDING o FAILED) y no puede reembolsarse."),
            @ApiResponse(responseCode = "404", description = "El pago no existe o el usuario no puede consultarlo.")
    })
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<?> refundPayment(@PathVariable Long paymentId) {
        if (!ownsPayment(paymentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("@currentUserAccess.ownsProvider(#providerId)")
    @Operation(operationId = "getPaymentsByProvider", summary = "Listar pagos del distribuidor",
            description = "Solo el proveedor autenticado propietario. Una consulta por proveedor,"
                    + " con comprador y orden. status es PaymentStatus; from/to son instantes ISO"
                    + " inclusivos sobre createdAt. Orden descendente por fecha e id. Moneda null"
                    + " porque no se persiste en pagos ni órdenes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagos propios o []",
                content = @Content(array = @ArraySchema(
                        schema = @Schema(implementation = ProviderPaymentResource.class)))),
        @ApiResponse(responseCode = "400", description = "Id, estado, fecha o período inválido"),
        @ApiResponse(responseCode = "403", description = "Sin rol proveedor o proveedor ajeno")
    })
    public ResponseEntity<List<ProviderPaymentResource>> getPaymentsByProvider(
            @PathVariable @Positive Long providerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var provider = tenantAccess.currentProviderId()
                .orElseThrow(() -> new AccessDeniedException(
                        "Provider identity required"));
        var payments = paymentQueryService.handle(
                new GetPaymentsByProviderIdQuery(provider, status, from, to));
        return ResponseEntity.ok(payments.stream()
                .map(ProviderPaymentResourceFromDomainAssembler::toResource)
                .toList());
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
