package com.primefuel.fulltank.platform.iam.application.internal.outboundservices.tokens;

public interface TokenService {
    String generateToken(String username);
    /** {@code persistent=false} issues a short-lived token for "do not keep me signed in" sessions. */
    String generateToken(String username, boolean persistent);
    String getUsernameFromToken(String token);
    java.time.Instant getIssuedAtFromToken(String token);
    boolean validateToken(String token);
}
