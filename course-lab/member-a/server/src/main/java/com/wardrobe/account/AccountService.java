package com.wardrobe.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final Duration sessionTtl;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accounts, PasswordEncoder passwords, Clock clock,
            @Value("${app.session-ttl}") Duration sessionTtl) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.clock = clock;
        if (sessionTtl.isZero() || sessionTtl.isNegative()) throw new IllegalArgumentException("Session TTL must be positive");
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public User register(String accountName, String password, String displayName) {
        String normalized = normalizedAccount(accountName);
        validatePassword(password);
        User user = new User(UUID.randomUUID().toString(), normalized, validatedName(displayName));
        try {
            accounts.insertUser(user, passwords.encode(password));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This account name is already registered.");
        }
        return user;
    }

    public record LoginResult(String token, User user) {}

    @Transactional
    public LoginResult login(String accountName, String password) {
        String normalized = normalizedAccount(accountName);
        validatePassword(password);
        AccountRepository.StoredAccount stored = accounts.findByAccount(normalized)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect account name or password."));
        if (!passwords.matches(password, stored.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect account name or password.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        accounts.insertSession(UUID.randomUUID().toString(), stored.user().id(), hash(token), clock.instant().plus(sessionTtl));
        return new LoginResult(token, stored.user());
    }

    /** Other business modules must call this before reading owner-scoped data. */
    public User requireUser(String authorization) {
        String token = bearerToken(authorization);
        return accounts.findUserForSession(hash(token), clock.instant())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."));
    }

    @Transactional
    public User updateProfile(String authorization, String displayName) {
        User user = requireUser(authorization);
        String name = validatedName(displayName);
        accounts.updateName(user.id(), name);
        return new User(user.id(), user.accountName(), name);
    }

    @Transactional
    public void logout(String authorization) {
        requireUser(authorization);
        accounts.revokeSession(hash(bearerToken(authorization)), clock.instant());
    }

    private String normalizedAccount(String value) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_]{3,32}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use 3-32 letters, numbers or underscores for the account name.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String value) {
        int bytes = value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must contain 8-72 UTF-8 bytes. Spaces are preserved.");
        }
    }

    private String validatedName(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Display name must contain 1-40 characters.");
        }
        return value.trim();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid login session. Please sign in again.");
        }
        return token;
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
