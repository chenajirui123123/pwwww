package com.example.wardrobemanager.view;

import android.os.Bundle;
import android.view.View;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.model.User;

public final class LoginActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); page(R.layout.activity_login);
        findViewById(R.id.primaryButton).setOnClickListener(v -> withNetwork(() -> controller.login(text(R.id.accountInput), text(R.id.passwordInput))));
        findViewById(R.id.secondaryButton).setOnClickListener(v -> go(RegisterActivity.class));
        findViewById(R.id.settingsButton).setOnClickListener(v -> settings());
        if (!session.token().isEmpty()) withNetwork(() -> controller.me());
    }
    @Override public void success(String action, User user) { resetTo(HomeActivity.class); }
    @Override public void unauthorized() { error("Your session has ended. Please sign in again."); }
}
