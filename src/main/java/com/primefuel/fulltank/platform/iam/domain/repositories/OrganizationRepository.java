package com.primefuel.fulltank.platform.iam.domain.repositories;

import com.primefuel.fulltank.platform.iam.domain.model.aggregates.Organization;

import java.util.Optional;

public interface OrganizationRepository {
    Optional<Organization> findById(Long id);
    Optional<Organization> findByRuc(String ruc);
    Organization save(Organization organization);
    boolean existsById(Long id);
}
