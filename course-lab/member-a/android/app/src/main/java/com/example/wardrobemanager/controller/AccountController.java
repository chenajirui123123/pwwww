package com.example.wardrobemanager.controller;

import android.os.Handler;
import android.os.Looper;
import com.example.wardrobemanager.model.AccountRepository;
import com.example.wardrobemanager.model.SessionStore;
import com.example.wardrobemanager.model.User;
import com.example.wardrobemanager.model.Validation;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountController {
    public interface View {
        void busy(boolean busy);
        void error(String message);
        void success(String action, User user);
        void unauthorized();
    }
    private final View view;
    private final AccountRepository repository;
    private final SessionStore session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private boolean running;
    private interface Work { JSONObject execute() throws Exception; }
    public AccountController(View view, AccountRepository repository, SessionStore session) {
        this.view = view; this.repository = repository; this.session = session;
    }
    public void login(String account, String password) {
        if (account.trim().isEmpty() || password.isEmpty()) { view.error("Enter your account and password."); return; }
        run("login", () -> repository.request("POST", "/api/auth/login", "", new JSONObject()
                .put("accountName", Validation.account(account)).put("password", password)));
    }
    public void register(String account, String password, String confirmation, String name) {
        String error = Validation.registrationError(account, password, confirmation, name);
        if (error != null) { view.error(error); return; }
        run("register", () -> repository.request("POST", "/api/auth/register", "", new JSONObject()
                .put("accountName", Validation.account(account)).put("password", password).put("displayName", name.trim())));
    }
    public void me() { run("me", () -> repository.request("GET", "/api/me", session.token(), null)); }
    public void update(String name) {
        if (name.trim().isEmpty() || name.trim().length() > 40) { view.error("Enter a display name of 1–40 characters."); return; }
        run("update", () -> repository.request("PATCH", "/api/me", session.token(), new JSONObject().put("displayName", name.trim())));
    }
    public void logout() { run("logout", () -> repository.request("POST", "/api/auth/logout", session.token(), null)); }
    private void run(String action, Work work) {
        if (closed || running) return;
        running = true; view.busy(true);
        executor.execute(() -> {
            try {
                JSONObject result = work.execute();
                User user = result.has("user") ? User.fromJson(result.getJSONObject("user")) : null;
                main.post(() -> {
                    if (closed) return;
                    running = false; view.busy(false);
                    try {
                        if (action.equals("login")) session.save(result.getString("token"));
                        if (action.equals("logout")) session.clear();
                    } catch (Exception e) {
                        session.clear();
                        view.error("Could not store the login state. Please sign in again.");
                        return;
                    }
                    view.success(action, user);
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (closed) return;
                    running = false; view.busy(false);
                    if (failure instanceof AccountRepository.ApiException) {
                        AccountRepository.ApiException api = (AccountRepository.ApiException) failure;
                        if (api.status == 401 && !action.equals("login") && !action.equals("register")) {
                            session.clear(); view.unauthorized();
                        } else view.error(api.getMessage());
                    } else view.error("Cannot reach the server. Check the connection and try again.");
                });
            }
        });
    }
    public void close() { closed = true; executor.shutdownNow(); main.removeCallbacksAndMessages(null); }
}
