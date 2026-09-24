package com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.adapters;

import com.primefuel.fulltank.platform.iam.application.ports.PasswordResetToken;
import com.primefuel.fulltank.platform.iam.application.ports.PasswordResetTokenStore;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.PasswordResetTokenEntity;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.PasswordResetTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class PasswordResetTokenStoreAdapter implements PasswordResetTokenStore {

    private final PasswordResetTokenRepository repository;

    public PasswordResetTokenStoreAdapter(PasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void deleteByUserId(Long userId) {
        repository.deleteByUserId(userId);
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return toToken(repository.save(toEntity(token)));
    }

    @Override
    public Optional<PasswordResetToken> lockValidToken(String tokenHash, Instant now) {
        return repository.lockValidToken(tokenHash, now).map(PasswordResetTokenStoreAdapter::toToken);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private static PasswordResetTokenEntity toEntity(PasswordResetToken token) {
        var entity = new PasswordResetTokenEntity();
        entity.setId(token.id());
        entity.setUserId(token.userId());
        entity.setTokenHash(token.tokenHash());
        entity.setExpiresAt(token.expiresAt());
        return entity;
    }

    private static PasswordResetToken toToken(PasswordResetTokenEntity entity) {
        return new PasswordResetToken(entity.getId(), entity.getUserId(), entity.getTokenHash(), entity.getExpiresAt());
    }
}
