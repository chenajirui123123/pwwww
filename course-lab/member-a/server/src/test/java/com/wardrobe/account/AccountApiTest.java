package com.wardrobe.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:accounts_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "app.session-ttl=PT24H"
})
@AutoConfigureMockMvc
class AccountApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetDatabase() {
        jdbc.update("DELETE FROM sessions");
        jdbc.update("DELETE FROM users");
    }

    private String body(Object value) throws Exception { return json.writeValueAsString(value); }

    private void register(String account, String name) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", account, "password", "Pass12345", "displayName", name))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.id").isString())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    private String login(String account) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", account, "password", "Pass12345"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("token").asText();
    }

    @Test void registrationPersistsOnlyPasswordHashAndNormalizesAccountName() throws Exception {
        register("  Alice_01  ", " Alice ");
        assertThat(jdbc.queryForObject("SELECT account_name FROM users", String.class)).isEqualTo("alice_01");
        assertThat(jdbc.queryForObject("SELECT display_name FROM users", String.class)).isEqualTo("Alice");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("$2").isNotEqualTo("Pass12345");
        String token = login("ALICE_01");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.accountName").value("alice_01"));
    }

    @Test void normalizedDuplicateAccountIsRejected() throws Exception {
        register("alice", "Alice");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", " ALICE ", "password", "Pass12345", "displayName", "Other"))))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test void profileUpdateUsesSessionIdentityAndCannotChangeAnotherAccount() throws Exception {
        register("alice", "Alice"); register("bobby", "Bob");
        String alice = login("alice"), bob = login("bobby");
        String bobId = jdbc.queryForObject("SELECT user_id FROM users WHERE account_name = 'bobby'", String.class);
        mvc.perform(patch("/api/me").header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("displayName", "Alice Updated", "userId", bobId))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("Alice Updated"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + bob))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("Bob"));
    }

    @Test void logoutRevokesOnlyCurrentSessionAndRejectsReusedToken() throws Exception {
        register("alice", "Alice");
        String first = login("alice"), second = login("alice");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test void rawSessionTokenIsNotStoredAndExpiredTokenIsRejected() throws Exception {
        register("alice", "Alice");
        String token = login("alice");
        String stored = jdbc.queryForObject("SELECT token_hash FROM sessions", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(token);
        jdbc.update("UPDATE sessions SET expires_at = ?", Timestamp.from(Instant.now().minusSeconds(10)));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void missingAndForgedCredentialsAreRejected() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"No\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + "x".repeat(43))).andExpect(status().isUnauthorized());
    }

    @Test void wrongPasswordDoesNotCreateSession() throws Exception {
        register("alice", "Alice");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", "alice", "password", "Wrong1234"))))
            .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sessions", Integer.class)).isZero();
    }

    @Test void invalidFieldsAndOversizedUtf8PasswordAreRejectedWithoutWrites() throws Exception {
        for (String password : new String[]{"short", "a".repeat(73), "密".repeat(25)}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("accountName", "alice", "password", password, "displayName", "Alice"))))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", "a!", "password", "Pass12345", "displayName", "Alice"))))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("accountName", "alice", "password", "Pass12345", "displayName", "   "))))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test void invalidProfileDoesNotOverwriteSavedName() throws Exception {
        register("alice", "Alice");
        String token = login("alice");
        mvc.perform(patch("/api/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("displayName", " ")))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT display_name FROM users", String.class)).isEqualTo("Alice");
    }

    @Test void malformedJsonIsHandledWithoutExposingServerDetails() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString())
            .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
