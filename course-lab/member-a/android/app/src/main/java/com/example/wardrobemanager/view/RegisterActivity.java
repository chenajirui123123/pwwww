package com.example.wardrobemanager.view;

import android.os.Bundle;
import android.widget.Toast;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.model.User;

public final class RegisterActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); page(R.layout.activity_register);
        findViewById(R.id.primaryButton).setOnClickListener(v -> withNetwork(() -> controller.register(text(R.id.accountInput),
                text(R.id.passwordInput), text(R.id.confirmPasswordInput), text(R.id.displayNameInput))));
        findViewById(R.id.secondaryButton).setOnClickListener(v -> finish());
    }
    @Override public void success(String action, User user) {
        Toast.makeText(this, "Account created. You can now sign in.", Toast.LENGTH_LONG).show();
        resetTo(LoginActivity.class);
    }
}
