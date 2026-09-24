package com.primefuel.fulltank.platform.iam.application.internal.queryservices;

import com.primefuel.fulltank.platform.iam.application.ports.UserRecipientLookup;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserRecipientLookupService implements UserRecipientLookup {
    private final UserRepository users;

    public UserRecipientLookupService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<Long> findByCompanyId(Long companyId) {
        return users.findByCompanyId(companyId).map(user -> user.getId());
    }

    @Override
    public Optional<Long> findByProviderId(Long providerId) {
        return users.findByProviderId(providerId).map(user -> user.getId());
    }
}
