package com.example.wardrobemanager.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.BuildConfig;
import com.example.wardrobemanager.controller.AccountController;
import com.example.wardrobemanager.model.AccountRepository;
import com.example.wardrobemanager.model.EndpointStore;
import com.example.wardrobemanager.model.SessionStore;
import com.example.wardrobemanager.model.User;

public abstract class BaseActivity extends Activity implements AccountController.View {
    protected AccountController controller;
    protected SessionStore session;
    private Runnable pendingNetworkAction;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionStore(this);
        controller = new AccountController(this, new AccountRepository(EndpointStore.get(this)), session);
    }
    protected void page(int layout) {
        setContentView(layout);
        View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        int left = root.getPaddingLeft(), top = root.getPaddingTop(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        // Android may restore EditText content automatically. Passwords must not survive recreation.
        for (int id : new int[]{R.id.passwordInput, R.id.confirmPasswordInput}) {
            View input = findViewById(id);
            if (input != null) input.setSaveEnabled(false);
        }
    }
    protected String text(int id) { return ((TextView) findViewById(id)).getText().toString(); }
    protected void withNetwork(Runnable action) {
        // Local HTTP is used only for classroom debugging. API 37 gates LAN access.
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37
                && EndpointStore.get(this).startsWith("http://")
                && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingNetworkAction = action;
            requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, 501);
        } else action.run();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 501) {
            Runnable action = pendingNetworkAction; pendingNetworkAction = null;
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (action != null) action.run();
            } else error("Allow local network access to connect to the development server, then retry.");
        }
    }
    protected void go(Class<?> target) { startActivity(new Intent(this, target)); }
    protected void resetTo(Class<?> target) {
        startActivity(new Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
    @Override public void busy(boolean value) {
        View progress = findViewById(R.id.progress);
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
        for (int id : new int[]{R.id.primaryButton, R.id.secondaryButton, R.id.settingsButton}) {
            View button = findViewById(id);
            if (button != null) button.setEnabled(!value);
        }
        View error = findViewById(R.id.errorText);
        if (value && error != null) error.setVisibility(View.GONE);
    }
    @Override public void error(String message) {
        TextView label = findViewById(R.id.errorText);
        if (label == null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        else { label.setText(message); label.setVisibility(View.VISIBLE); label.announceForAccessibility(message); }
    }
    @Override public void unauthorized() { resetTo(LoginActivity.class); }
    @Override public void success(String action, User user) {}
    protected void settings() {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(EndpointStore.get(this)); input.setPadding(32, 24, 32, 24);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connection settings")
                .setMessage("Emulator: http://10.0.2.2:8080\nUSB device with adb reverse: http://127.0.0.1:8080")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                EndpointStore.save(this, input.getText().toString());
                session.clear(); dialog.dismiss(); resetTo(LoginActivity.class);
            } catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        }));
        dialog.show();
    }
    @Override protected void onDestroy() { controller.close(); super.onDestroy(); }
}
