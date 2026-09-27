package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.application.commandservices.BuyerCompanyCommandService;
import com.primefuel.fulltank.platform.iam.application.queryservices.BuyerCompanyQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetAllBuyerCompaniesQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetBuyerCompanyByIdQuery;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.BuyerCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.CreateBuyerCompanyResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.BuyerCompanyResourceFromEntityAssembler;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.CreateBuyerCompanyCommandFromResourceAssembler;
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
@RequestMapping(value = "/api/v1/buyer-companies", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Empresas compradoras", description = "Registro, consulta y actualización de perfiles de empresas compradoras")
public class BuyerCompaniesController {

    private final BuyerCompanyCommandService buyerCompanyCommandService;
    private final BuyerCompanyQueryService buyerCompanyQueryService;
    private final BuyerCompanyRepository buyerCompanyRepository;

    public BuyerCompaniesController(BuyerCompanyCommandService buyerCompanyCommandService,
                                    BuyerCompanyQueryService buyerCompanyQueryService,
                                    BuyerCompanyRepository buyerCompanyRepository) {
        this.buyerCompanyCommandService = buyerCompanyCommandService;
        this.buyerCompanyQueryService = buyerCompanyQueryService;
        this.buyerCompanyRepository = buyerCompanyRepository;
    }

    /**
     * Registra un perfil independiente de empresa compradora.
     *
     * <p>Es una operación pública de autoservicio: no requiere autenticación ni crea una cuenta de usuario o membresía.
     * Los fallos de persistencia se responden como error del servidor.</p>
     */
    @Operation(summary = "Registrar empresa compradora",
            description = "Persiste un nuevo perfil de empresa compradora. La ruta está disponible sin autenticación y no crea usuarios ni membresías.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil de empresa compradora creado."),
            @ApiResponse(responseCode = "500", description = "Se produjo un error inesperado al guardar el perfil.")
    })
    @PostMapping
    public ResponseEntity<?> createBuyerCompany(@RequestBody CreateBuyerCompanyResource resource) {
        var command = CreateBuyerCompanyCommandFromResourceAssembler.toCommandFromResource(resource);
        var result = buyerCompanyCommandService.handle(command);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result,
                BuyerCompanyResourceFromEntityAssembler::toResourceFromEntity,
                HttpStatus.CREATED);
    }

    /**
     * Lista todas las empresas compradoras registradas.
     *
     * <p>Requiere la autoridad ROLE_ADMIN.</p>
     */
    @Operation(summary = "Listar empresas compradoras",
            description = "Devuelve todos los perfiles registrados. Solo está disponible para administradores de plataforma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Se devuelve la lista de empresas compradoras."),
            @ApiResponse(responseCode = "403", description = "El usuario no cuenta con autoridad ROLE_ADMIN.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<BuyerCompanyResource>> getAllBuyerCompanies() {
        var companies = buyerCompanyQueryService.handle(new GetAllBuyerCompaniesQuery());
        var resources = companies.stream().map(BuyerCompanyResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Consulta una empresa compradora.
     *
     * <p>Solo puede consultarla el tenant propietario del perfil.</p>
     */
    @Operation(summary = "Consultar empresa compradora por identificador",
            description = "Devuelve el perfil indicado cuando pertenece al tenant comprador autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de empresa compradora devuelto."),
            @ApiResponse(responseCode = "403", description = "La empresa compradora no pertenece al usuario autenticado."),
            @ApiResponse(responseCode = "404", description = "La empresa compradora no existe.")
    })
    @GetMapping("/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<BuyerCompanyResource> getBuyerCompanyById(@PathVariable Long companyId) {
        var result = buyerCompanyQueryService.handle(new GetBuyerCompanyByIdQuery(companyId));
        return result.map(company -> new ResponseEntity<>(
                        BuyerCompanyResourceFromEntityAssembler.toResourceFromEntity(company), HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * Actualiza el perfil de una empresa compradora.
     *
     * <p>Solo el tenant propietario puede actualizarlo. Los campos editables se sustituyen con los valores recibidos.</p>
     */
    @Operation(summary = "Actualizar empresa compradora",
            description = "Reemplaza los campos editables del perfil identificado. Requiere que el usuario pertenezca a esa empresa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de empresa compradora actualizado."),
            @ApiResponse(responseCode = "403", description = "La empresa compradora no pertenece al usuario autenticado."),
            @ApiResponse(responseCode = "404", description = "La empresa compradora no existe.")
    })
    @PutMapping("/{companyId}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#companyId)")
    public ResponseEntity<BuyerCompanyResource> updateBuyerCompany(@PathVariable Long companyId,
                                                                   @RequestBody CreateBuyerCompanyResource resource) {
        var result = buyerCompanyRepository.findById(companyId);
        if (result.isEmpty()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        var company = result.get();
        company.setName(resource.name());
        company.setRuc(resource.ruc());
        company.setSector(resource.sector());
        company.setAddress(resource.address());
        company.setContactEmail(resource.contactEmail());
        company.setPhone(resource.phone());
        var updated = buyerCompanyRepository.save(company);
        return new ResponseEntity<>(BuyerCompanyResourceFromEntityAssembler.toResourceFromEntity(updated), HttpStatus.OK);
    }
}
