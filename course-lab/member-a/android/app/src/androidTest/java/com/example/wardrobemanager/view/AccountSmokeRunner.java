package com.example.wardrobemanager.view;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.model.EndpointStore;
import com.example.wardrobemanager.model.SessionStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real emulator + live backend smoke test. Run only against a disposable test database. */
public final class AccountSmokeRunner extends Instrumentation {
    private String baseUrl;
    private Activity current;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        baseUrl = arguments.getString("baseUrl", "http://10.0.2.2:18080");
        start();
    }
    private void click(Activity activity, int id) { runOnMainSync(() -> activity.findViewById(id).performClick()); }
    private void input(Activity activity, int id, String text) {
        runOnMainSync(() -> ((EditText) activity.findViewById(id)).setText(text));
    }
    private ActivityMonitor monitor(Class<?> type) { return addMonitor(type.getName(), null, false); }
    private Activity await(ActivityMonitor monitor) {
        Activity activity = waitForMonitorWithTimeout(monitor, 15000); removeMonitor(monitor);
        if (activity == null) throw new AssertionError("Expected screen did not open");
        waitForIdleSync(); current = activity; return activity;
    }
    private void textContains(Activity activity, int id, String expected) throws Exception {
        for (int i = 0; i < 60; i++) {
            AtomicBoolean found = new AtomicBoolean();
            runOnMainSync(() -> found.set(((TextView) activity.findViewById(id)).getText().toString().contains(expected)));
            if (found.get()) return;
            Thread.sleep(250);
        }
        throw new AssertionError("Expected text: " + expected);
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync(); Thread.sleep(3800);
        File directory = new File(getTargetContext().getFilesDir(), "qa_screenshots");
        directory.mkdirs();
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream file = new FileOutputStream(new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, file);
        }
        bitmap.recycle();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            EndpointStore.save(getTargetContext(), baseUrl);
            new SessionStore(getTargetContext()).clear();
            Intent launch = new Intent(getTargetContext(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            Activity login = startActivitySync(launch);
            current = login;
            screenshot("01-login.png");
            ActivityMonitor registerMonitor = monitor(RegisterActivity.class);
            click(login, R.id.secondaryButton);
            Activity register = await(registerMonitor);
            screenshot("02-register.png");
            String account = "alex_" + System.currentTimeMillis();
            input(register, R.id.displayNameInput, "Alex");
            input(register, R.id.accountInput, account);
            input(register, R.id.passwordInput, "DemoPass123");
            input(register, R.id.confirmPasswordInput, "DemoPass123");
            ActivityMonitor loginMonitor = monitor(LoginActivity.class);
            click(register, R.id.primaryButton);
            login = await(loginMonitor);
            input(login, R.id.accountInput, account);
            input(login, R.id.passwordInput, "DemoPass123");
            ActivityMonitor homeMonitor = monitor(HomeActivity.class);
            click(login, R.id.primaryButton);
            HomeActivity home = (HomeActivity) await(homeMonitor);
            textContains(home, R.id.greetingText, "Alex");
            screenshot("03-home.png");
            ActivityMonitor profileMonitor = monitor(ProfileActivity.class);
            click(home, R.id.profileButton);
            Activity profile = await(profileMonitor);
            textContains(profile, R.id.accountText, account);
            screenshot("04-profile.png");
            input(profile, R.id.displayNameInput, "Alex New");
            click(profile, R.id.primaryButton);
            // Wait until the asynchronous save re-enables its button.
            for (int i = 0; i < 60; i++) {
                AtomicBoolean enabled = new AtomicBoolean();
                runOnMainSync(() -> enabled.set(profile.findViewById(R.id.primaryButton).isEnabled()));
                if (enabled.get()) break;
                Thread.sleep(250);
            }
            click(profile, R.id.secondaryButton);
            textContains(home, R.id.greetingText, "Alex New");
            ActivityMonitor moduleMonitor = monitor(ModuleActivity.class);
            click(home, R.id.wardrobeCard);
            Activity module = await(moduleMonitor);
            screenshot("05-module-entry.png");
            click(module, R.id.backButton);
            waitForIdleSync(); Thread.sleep(500);
            loginMonitor = monitor(LoginActivity.class);
            runOnMainSync(() -> home.controller.logout());
            await(loginMonitor);
            if (!new SessionStore(getTargetContext()).token().isEmpty()) throw new AssertionError("Logout retained local token");
            result.putString("stream", "PASS: register, login, profile save/reload, module route and logout\n");
            new SessionStore(getTargetContext()).clear();
            EndpointStore.save(getTargetContext(), "http://10.0.2.2:8080");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try { screenshot("99-error.png"); } catch (Exception ignored) {}
            StringBuilder uiError = new StringBuilder();
            if (current != null) runOnMainSync(() -> {
                TextView label = current.findViewById(R.id.errorText);
                if (label != null) uiError.append(label.getText());
            });
            result.putString("stream", "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "; " + uiError + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
