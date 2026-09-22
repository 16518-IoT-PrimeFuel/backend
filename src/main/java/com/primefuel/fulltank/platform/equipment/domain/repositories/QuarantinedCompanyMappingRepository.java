package com.primefuel.fulltank.platform.equipment.domain.repositories;

import com.primefuel.fulltank.platform.equipment.domain.model.aggregates.QuarantinedCompanyMapping;

import java.util.Optional;

public interface QuarantinedCompanyMappingRepository {
    Optional<QuarantinedCompanyMapping> findByLegacyCompanyId(Long legacyCompanyId);
    QuarantinedCompanyMapping save(QuarantinedCompanyMapping mapping);
    long count();
}
