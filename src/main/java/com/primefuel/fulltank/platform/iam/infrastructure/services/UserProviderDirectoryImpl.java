package com.primefuel.fulltank.platform.iam.infrastructure.services;

import com.primefuel.fulltank.platform.iam.api.UserProviderDirectory;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserProviderDirectoryImpl implements UserProviderDirectory {

    private final UserRepository users;

    public UserProviderDirectoryImpl(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean belongsToProvider(Long userId, Long providerId) {
        return userId != null && providerId != null
                && users.findById(userId).map(user -> providerId.equals(user.getProviderId())).orElse(false);
    }
}
