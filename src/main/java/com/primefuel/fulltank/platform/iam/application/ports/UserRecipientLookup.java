package com.primefuel.fulltank.platform.iam.application.ports;

import java.util.Optional;

public interface UserRecipientLookup {
    Optional<Long> findByCompanyId(Long companyId);

    Optional<Long> findByProviderId(Long providerId);
}
