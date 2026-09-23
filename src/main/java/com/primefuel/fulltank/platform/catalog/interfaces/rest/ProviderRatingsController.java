package com.primefuel.fulltank.platform.catalog.interfaces.rest;

import com.primefuel.fulltank.platform.catalog.domain.model.aggregates.ProviderRating;
import com.primefuel.fulltank.platform.catalog.domain.repositories.ProviderRatingRepository;
import com.primefuel.fulltank.platform.catalog.interfaces.rest.resources.ProviderRatingResource;
import com.primefuel.fulltank.platform.iam.domain.repositories.BuyerCompanyRepository;
import com.primefuel.fulltank.platform.iam.domain.repositories.ProviderCompanyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/provider-ratings")
public class ProviderRatingsController {

    private final ProviderRatingRepository ratingRepository;
    private final BuyerCompanyRepository buyerCompanyRepository;
    private final ProviderCompanyRepository providerCompanyRepository;

    public ProviderRatingsController(ProviderRatingRepository ratingRepository,
                                     BuyerCompanyRepository buyerCompanyRepository,
                                     ProviderCompanyRepository providerCompanyRepository) {
        this.ratingRepository = ratingRepository;
        this.buyerCompanyRepository = buyerCompanyRepository;
        this.providerCompanyRepository = providerCompanyRepository;
    }

    /**
     * Lists provider ratings.
     *
     * <p>Any authenticated caller may list ratings; the optional filters narrow the result by buyer
     * company and/or provider company. Results are not tenant-scoped.</p>
     */
    @Operation(summary = "List provider ratings",
            description = "Returns provider ratings, optionally filtered by buyer company and/or provider company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ratings returned.")
    })
    @GetMapping
    public List<ProviderRatingResource> getAll(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long providerId) {
        return ratingRepository.findAll(companyId, providerId).stream()
                .map(ProviderRatingsController::toResource).toList();
    }

    /**
     * Creates a rating from a buyer company to a provider company.
     *
     * <p>Only the buyer company named in the body may create the rating, and it may rate a given
     * provider only once. The referenced buyer and provider companies must both exist; a missing
     * reference is answered as a bad request rather than a not found.</p>
     */
    @Operation(summary = "Create a provider rating",
            description = "Records a 1-to-5 rating from the caller's buyer company to a provider company.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rating created."),
            @ApiResponse(responseCode = "400", description = "The rating, company or provider is invalid, or the referenced company/provider does not exist."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the buyer company in the request body."),
            @ApiResponse(responseCode = "409", description = "The buyer company has already rated this provider.")
    })
    @PostMapping
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> create(@RequestBody ProviderRatingResource resource) {
        var validation = validate(resource);
        if (validation != null) return validation;
        if (ratingRepository.findByCompanyIdAndProviderId(resource.companyId(), resource.providerId()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("The buyer company already rated this provider");
        }
        try {
            var saved = ratingRepository.save(
                    new ProviderRating(resource.companyId(), resource.providerId(), resource.rating()));
            return new ResponseEntity<>(toResource(saved), HttpStatus.CREATED);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    /**
     * Updates the score of an existing rating.
     *
     * <p>Only the owning buyer company may update; the buyer and provider of a rating are immutable,
     * so changing either is rejected as a bad request.</p>
     */
    @Operation(summary = "Update a provider rating",
            description = "Changes the score of an existing rating owned by the caller's buyer company.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rating updated."),
            @ApiResponse(responseCode = "400", description = "The rating is invalid, the referenced company/provider does not exist, or the buyer/provider of the rating was changed."),
            @ApiResponse(responseCode = "403", description = "Caller does not own the buyer company in the request body."),
            @ApiResponse(responseCode = "404", description = "Rating does not exist.")
    })
    @PutMapping("/{id}")
    @PreAuthorize("@currentUserAccess.ownsCompany(#resource.companyId())")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ProviderRatingResource resource) {
        var validation = validate(resource);
        if (validation != null) return validation;
        var rating = ratingRepository.findById(id).orElse(null);
        if (rating == null) return ResponseEntity.notFound().build();
        if (!rating.getCompanyId().equals(resource.companyId())
                || !rating.getProviderId().equals(resource.providerId())) {
            return ResponseEntity.badRequest().body("Company and provider cannot be changed");
        }
        try {
            rating.changeRating(resource.rating());
            return ResponseEntity.ok(toResource(ratingRepository.save(rating)));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }
    }

    private ResponseEntity<?> validate(ProviderRatingResource resource) {
        if (resource.companyId() == null || resource.providerId() == null
                || resource.rating() == null || resource.rating() < 1 || resource.rating() > 5) {
            return ResponseEntity.badRequest().body("A valid company, provider and rating from 1 to 5 are required");
        }
        if (buyerCompanyRepository.findById(resource.companyId()).isEmpty()
                || providerCompanyRepository.findById(resource.providerId()).isEmpty()) {
            return ResponseEntity.badRequest().body("Buyer company or provider does not exist");
        }
        return null;
    }

    private static ProviderRatingResource toResource(ProviderRating rating) {
        return new ProviderRatingResource(rating.getId(), rating.getCompanyId(),
                rating.getProviderId(), rating.getRating());
    }
}
