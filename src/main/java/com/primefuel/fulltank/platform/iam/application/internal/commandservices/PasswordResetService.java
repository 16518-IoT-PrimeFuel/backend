package com.primefuel.fulltank.platform.iam.application.internal.commandservices;

import com.primefuel.fulltank.platform.iam.application.internal.outboundservices.hashing.HashingService;
import com.primefuel.fulltank.platform.iam.domain.repositories.UserRepository;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.entities.PasswordResetTokenEntity;
import com.primefuel.fulltank.platform.iam.infrastructure.persistence.jpa.repositories.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final HashingService hashing;
    private final JavaMailSender mailSender;
    private final String resetLink;
    private final String mailFrom;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
            HashingService hashing, JavaMailSender mailSender,
            @Value("${app.password-reset.link}") String resetLink,
            @Value("${app.password-reset.from}") String mailFrom) {
        this.users = users;
        this.tokens = tokens;
        this.hashing = hashing;
        this.mailSender = mailSender;
        this.resetLink = resetLink;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public void request(String email) {
        if (email == null || email.isBlank()) return;
        var user = users.findByUsername(email.trim());
        if (user.isEmpty()) return;

        tokens.deleteByUserId(user.get().getId());
        var raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        var token = HexFormat.of().formatHex(raw);
        var reset = new PasswordResetTokenEntity();
        reset.setUserId(user.get().getId());
        reset.setTokenHash(hash(token));
        reset.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        tokens.save(reset);

        var message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.get().getUsername());
        message.setSubject("Restablecer contraseña de FullTank");
        message.setText("Usa este enlace dentro de los próximos 30 minutos para cambiar tu contraseña:\n\n"
                + resetLink + (resetLink.contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "\n\nSi no solicitaste este cambio, ignora este mensaje.");
        mailSender.send(message);
    }

    @Transactional
    public void confirm(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset request");
        }
        var entity = tokens.lockValidToken(hash(token), Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired reset request"));
        var user = users.findById(entity.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired reset request"));
        user.setPassword(hashing.encode(newPassword));
        users.save(user);
        tokens.delete(entity);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
