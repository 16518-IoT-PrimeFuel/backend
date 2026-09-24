package com.primefuel.fulltank.platform.iam.application.ports;

import java.time.Instant;

public record PasswordResetToken(Long id, Long userId, String tokenHash, Instant expiresAt) {
}
