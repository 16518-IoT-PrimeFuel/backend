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
@Tag(name = "Users", description = "Users management endpoints")
public class UsersController {

    private final UserQueryService userQueryService;

    public UsersController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    /**
     * Lists every user in the platform.
     *
     * <p>Administrative endpoint; restricted to callers holding the ROLE_ADMIN authority.</p>
     */
    @Operation(summary = "List all users",
            description = "Returns every registered user. Restricted to administrators.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users returned."),
            @ApiResponse(responseCode = "403", description = "Caller does not hold the ROLE_ADMIN authority.")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserResource>> getAllUsers() {
        var users = userQueryService.handle(new GetAllUsersQuery());
        var resources = users.stream().map(UserResourceFromEntityAssembler::toResourceFromEntity).toList();
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    /**
     * Retrieves a single user.
     *
     * <p>A user may only read their own profile.</p>
     */
    @Operation(summary = "Get a user by id",
            description = "Returns the user identified by the path id when the caller is that same user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User returned."),
            @ApiResponse(responseCode = "403", description = "Caller is not the requested user."),
            @ApiResponse(responseCode = "404", description = "User does not exist.")
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
