package com.primefuel.fulltank.platform.iam.interfaces.rest;

import com.primefuel.fulltank.platform.iam.api.MembershipAccess;
import com.primefuel.fulltank.platform.iam.application.queryservices.MembershipQueryService;
import com.primefuel.fulltank.platform.iam.application.queryservices.OrganizationQueryService;
import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Membership;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetMembershipsByUserIdQuery;
import com.primefuel.fulltank.platform.iam.domain.model.queries.GetOrganizationByIdQuery;
import com.primefuel.fulltank.platform.iam.interfaces.rest.resources.OrganizationResource;
import com.primefuel.fulltank.platform.iam.interfaces.rest.transform.OrganizationResourceFromDomainAssembler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v2/me/organizations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organizations", description = "Organizations the authenticated user belongs to (v2)")
public class MyOrganizationsController {

    private final MembershipAccess membershipAccess;
    private final MembershipQueryService membershipQueryService;
    private final OrganizationQueryService organizationQueryService;

    public MyOrganizationsController(MembershipAccess membershipAccess,
                                     MembershipQueryService membershipQueryService,
                                     OrganizationQueryService organizationQueryService) {
        this.membershipAccess = membershipAccess;
        this.membershipQueryService = membershipQueryService;
        this.organizationQueryService = organizationQueryService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResource>> getMyOrganizations() {
        var userId = membershipAccess.currentUserId();
        if (userId.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        var resources = membershipQueryService.handle(new GetMembershipsByUserIdQuery(userId.get())).stream()
                .filter(Membership::isActive)
                .map(membership -> organizationQueryService.handle(new GetOrganizationByIdQuery(membership.getOrganizationId()))
                        .map(organization -> OrganizationResourceFromDomainAssembler.toResourceFromDomain(organization, membership.getRole()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }
}
