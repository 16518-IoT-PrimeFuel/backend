package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.queryservices.UserQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetAllUsersQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetUserByIdQuery;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.UserResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.UserResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Usuarios", description = "Consulta de cuentas de usuario")
public class UsersController {

    private final UserQueryService userQueryService;

    public UsersController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    /**
     * Lista todas las cuentas de usuario de la plataforma.
     *
     * <p>Solo pueden consultarlo administradores con la autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar usuarios",
            description = "Devuelve todas las cuentas registradas; requiere la autoridad ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de usuarios devuelta."),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no tiene la autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserResource>> getAllUsers() {
        var users = userQueryService.handle(new GetAllUsersQuery());
        var resources = users.stream().map(UserResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta una cuenta de usuario por su identificador.
     *
     * <p>Solo se permite consultar el perfil del usuario autenticado.</p>
     */
    @Operation(summary = "Consultar usuario por identificador",
            description = "Devuelve el perfil indicado cuando pertenece al usuario autenticado; la identidad no se acepta desde el cuerpo de la solicitud.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de usuario devuelto."),
            @ApiResponse(responseCode = "403", description = "El perfil solicitado no pertenece al usuario autenticado."),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta solicitada.")
    })
    @GetMapping("/{userId}")
    @PreAuthorize("@currentUserAccess.ownsUser(#userId)")
    public ResponseEntity<UserResource> getUserById(@PathVariable Long userId) {
        var result = userQueryService.handle(new GetUserByIdQuery(userId));
        return result.map(user -> new ResponseEntity<>(
                        UserResourceFromEntityAssembler.toResourceFromEntity(user), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
