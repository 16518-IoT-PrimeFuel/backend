package com.primefuel.fulltank.platform.iam.application.ports;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenStore {

    void deleteByUserId(Long userId);

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> lockValidToken(String tokenHash, Instant now);

    void deleteById(Long id);
}
