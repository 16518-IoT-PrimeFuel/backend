package com.primefuel.fulltank.platform.equipment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import com.primefuel.fulltank.platform.equipment.domain.model.valueobjects.SiteType;

public record CreateSiteResource(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String address,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        SiteType siteType) {
}
