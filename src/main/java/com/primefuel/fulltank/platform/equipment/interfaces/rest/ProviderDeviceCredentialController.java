package com.primefuel.fulltank.platform.equipment.interfaces.rest;

import com.primefuel.fulltank.platform.equipment.api.ProviderBuyerAccess;
import com.primefuel.fulltank.platform.equipment.devicebinding.application.internal.commandservices.DeviceCredentialServiceImpl;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.aggregates.DeviceBinding;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.ProvisionDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RevokeDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.model.commands.RotateDeviceCredentialCommand;
import com.primefuel.fulltank.platform.equipment.devicebinding.domain.repositories.DeviceBindingRepository;
import com.primefuel.fulltank.platform.equipment.interfaces.rest.resources.DeviceCredentialResource;
import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.api.TenantAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.primefuel.fulltank.platform.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Credencial técnica (token rotativo) del canal de un tanque del distribuidor. Solo el distribuidor dueño del
 * tanque (vínculo con el comprador) puede gestionarla; a otro distribuidor el tanque le responde como no
 * encontrado.
 */
@RestController
@RequestMapping(value = "/api/provider/tanks/{tankId}/device-credential", produces = "application/json")
@PreAuthorize("@currentUserAccess.isProvider()")
@Tag(name = "Tanques del distribuidor")
public class ProviderDeviceCredentialController {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderDeviceCredentialController.class);

    private final DeviceCredentialServiceImpl credentials;
    private final DeviceBindingRepository bindings;
    private final ProviderBuyerAccess buyers;
    private final TenantAccess access;
    private final MembershipAccess membership;

    public ProviderDeviceCredentialController(DeviceCredentialServiceImpl credentials,
                                              DeviceBindingRepository bindings,
                                              ProviderBuyerAccess buyers,
                                              TenantAccess access,
                                              MembershipAccess membership) {
        this.credentials = credentials;
        this.bindings = bindings;
        this.buyers = buyers;
        this.access = access;
        this.membership = membership;
    }

    @PostMapping
    @Operation(summary = "Emitir credencial del dispositivo",
            description = "Emite el token técnico del canal IoT abierto del tanque. El token en claro se devuelve "
                    + "una sola vez; el servidor solo guarda su hash. Si el canal ya tiene credencial activa, "
                    + "responde 409: usar rotate.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credencial emitida; token presente una única vez."),
            @ApiResponse(responseCode = "400", description = "El tanque tiene varios canales; indicar channel."),
            @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado o rol insuficiente."),
            @ApiResponse(responseCode = "404", description = "Tanque ajeno o sin vínculo IoT abierto."),
            @ApiResponse(responseCode = "409", description = "El canal ya tiene una credencial activa.")
    })
    public ResponseEntity<?> issue(@PathVariable @Positive Long tankId,
                                   @RequestParam(required = false) String channel) {
        var binding = openBinding(tankId, channel);
        audit("ISSUE", tankId, binding);
        var result = credentials.handle(new ProvisionDeviceCredentialCommand(binding.getDeviceId(), binding.getChannel()));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                issued -> new DeviceCredentialResource(tankId, issued.deviceId(), issued.channel(),
                        issued.credentialId(), issued.tokenVersion(), issued.rawToken(), issued.issuedAt(), null),
                HttpStatus.CREATED);
    }

    @PostMapping("/rotate")
    @Operation(summary = "Rotar credencial del dispositivo",
            description = "Revoca la credencial activa del canal y emite una nueva. El token anterior deja de "
                    + "autenticar de inmediato. El nuevo token en claro se devuelve una sola vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credencial rotada; token nuevo presente una única vez."),
            @ApiResponse(responseCode = "400", description = "El tanque tiene varios canales; indicar channel."),
            @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado o rol insuficiente."),
            @ApiResponse(responseCode = "404", description = "Tanque ajeno o sin vínculo IoT abierto.")
    })
    public ResponseEntity<?> rotate(@PathVariable @Positive Long tankId,
                                    @RequestParam(required = false) String channel) {
        var binding = openBinding(tankId, channel);
        audit("ROTATE", tankId, binding);
        var result = credentials.handle(new RotateDeviceCredentialCommand(binding.getDeviceId(), binding.getChannel()));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                rotated -> new DeviceCredentialResource(tankId, rotated.deviceId(), rotated.channel(),
                        rotated.credentialId(), rotated.tokenVersion(), rotated.rawToken(), rotated.issuedAt(), null),
                HttpStatus.OK);
    }

    @PostMapping("/revoke")
    @Operation(summary = "Revocar credencial del dispositivo",
            description = "Revoca la credencial activa del canal. Las lecturas posteriores con ese token quedan en "
                    + "cuarentena (202 en la ingesta).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credencial revocada."),
            @ApiResponse(responseCode = "400", description = "El tanque tiene varios canales; indicar channel."),
            @ApiResponse(responseCode = "403", description = "Sin proveedor autenticado o rol insuficiente."),
            @ApiResponse(responseCode = "404", description = "Tanque ajeno, sin vínculo IoT abierto o sin credencial activa.")
    })
    public ResponseEntity<?> revoke(@PathVariable @Positive Long tankId,
                                    @RequestParam(required = false) String channel) {
        var binding = openBinding(tankId, channel);
        audit("REVOKE", tankId, binding);
        var revokedAt = Instant.now();
        var result = credentials.handle(new RevokeDeviceCredentialCommand(binding.getDeviceId(), binding.getChannel()));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                credentialId -> new DeviceCredentialResource(tankId, binding.getDeviceId(), binding.getChannel(),
                        credentialId, null, null, null, revokedAt),
                HttpStatus.OK);
    }

    /**
     * Registra la acción sin credenciales: solo tanque, proveedor actuante, usuario y canal. El token nunca entra
     * en el log (ni aquí ni en DeviceCredentialResource/ProvisionedCredential, que lo enmascaran en toString).
     */
    private void audit(String action, Long tankId, DeviceBinding binding) {
        LOG.info("device-credential action={} tankId={} providerId={} userId={} deviceId={} channel={}",
                action, tankId, access.currentProviderId().orElse(null),
                membership.currentUserId().orElse(null), binding.getDeviceId(), binding.getChannel());
    }

    /**
     * Resuelve el canal abierto del tanque tras comprobar que el distribuidor autenticado está vinculado al tanque.
     *
     * <p>La puerta es {@link ProviderBuyerAccess#canReadTank}: el distribuidor debe tener un vínculo real con la
     * organización del tanque (vínculo comercial explícito, solicitud de reposición u orden). Es la misma regla que
     * ya gobierna {@code PUT /api/provider/tanks/{tankId}}, que puede cambiar el dispositivo del tanque; por eso
     * emitir, rotar y revocar la credencial no exige una regla más estricta. Sin vínculo responde 404 y no revela
     * la existencia del tanque.</p>
     */
    private DeviceBinding openBinding(Long tankId, String channel) {
        var provider = access.currentProviderId()
                .orElseThrow(() -> new AccessDeniedException("Provider identity or ownership required"));
        if (!buyers.canReadTank(provider, tankId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var candidates = bindings.findOpenByTankId(tankId).stream()
                .filter(binding -> channel == null || channel.equals(binding.getChannel()))
                .toList();
        if (candidates.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The tank has several device channels; channel is required");
        }
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return candidates.get(0);
    }
}
