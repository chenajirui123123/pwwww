package com.wardrobe.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;
    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record StoredAccount(User user, String passwordHash) {}

    public Optional<StoredAccount> findByAccount(String accountName) {
        return jdbc.query("SELECT user_id, account_name, display_name, password_hash FROM users WHERE account_name = ?",
            (rs, row) -> new StoredAccount(new User(rs.getString("user_id"), rs.getString("account_name"), rs.getString("display_name")), rs.getString("password_hash")),
            accountName).stream().findFirst();
    }

    public void insertUser(User user, String passwordHash) {
        jdbc.update("INSERT INTO users(user_id, account_name, password_hash, display_name) VALUES(?, ?, ?, ?)",
            user.id(), user.accountName(), passwordHash, user.displayName());
    }

    public void insertSession(String id, String ownerId, String tokenHash, Instant expiresAt) {
        jdbc.update("INSERT INTO sessions(session_id, owner_id, token_hash, expires_at) VALUES(?, ?, ?, ?)",
            id, ownerId, tokenHash, Timestamp.from(expiresAt));
    }

    public Optional<User> findUserForSession(String tokenHash, Instant now) {
        return jdbc.query("SELECT u.user_id, u.account_name, u.display_name FROM sessions s JOIN users u ON s.owner_id = u.user_id "
                + "WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?",
            (rs, row) -> new User(rs.getString("user_id"), rs.getString("account_name"), rs.getString("display_name")),
            tokenHash, Timestamp.from(now)).stream().findFirst();
    }

    public void updateName(String ownerId, String displayName) {
        jdbc.update("UPDATE users SET display_name = ? WHERE user_id = ?", displayName, ownerId);
    }

    public void revokeSession(String tokenHash, Instant now) {
        jdbc.update("UPDATE sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL", Timestamp.from(now), tokenHash);
    }
}
