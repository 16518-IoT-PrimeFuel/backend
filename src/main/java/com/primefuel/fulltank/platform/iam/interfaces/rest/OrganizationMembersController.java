package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.queryservices.MembershipQueryService;
import com.primefuel.fulltank.platform.iam.application.queryservices.UserQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByOrganizationIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetUserByIdQuery;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.OrganizationMemberResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Miembros de organización", description = "Miembros activos de una organización")
public class OrganizationMembersController {

    private final MembershipQueryService membershipQueryService;
    private final UserQueryService userQueryService;

    public OrganizationMembersController(MembershipQueryService membershipQueryService,
                                         UserQueryService userQueryService) {
        this.membershipQueryService = membershipQueryService;
        this.userQueryService = userQueryService;
    }

    /**
     * Lista los miembros activos de una organización.
     *
     * <p>Solo los miembros activos de esa organización pueden consultarla; se expone únicamente el usuario y su rol.</p>
     */
    @Operation(summary = "Listar miembros de una organización",
            description = "Devuelve usuario y rol de cada membresía activa de la organización. Requiere pertenecer a ella.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de miembros activos."),
            @ApiResponse(responseCode = "403", description = "El usuario no pertenece a la organización indicada.")
    })
    @GetMapping("/{organizationId}/members")
    @PreAuthorize("@membershipAccess.belongsToOrganization(#organizationId)")
    public ResponseEntity<List<OrganizationMemberResource>> getMembers(@PathVariable Long organizationId) {
        var members = membershipQueryService.handle(new GetMembershipsByOrganizationIdQuery(organizationId)).stream()
                .map(membership -> new OrganizationMemberResource(
                        membership.getUserId(),
                        userQueryService.handle(new GetUserByIdQuery(membership.getUserId()))
                                .map(user -> user.getUsername())
                                .orElse(null),
                        membership.getRole().name()))
                .toList();
        return new ResponseEntity<>(members, HttpStatus.OK);
    }
}
