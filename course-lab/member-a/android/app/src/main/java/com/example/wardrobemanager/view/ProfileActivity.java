package com.example.wardrobemanager.view;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.model.User;

public final class ProfileActivity extends BaseActivity {
    private boolean loaded;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); page(R.layout.activity_profile);
        findViewById(R.id.primaryButton).setOnClickListener(v -> {
            withNetwork(() -> { if (loaded) controller.update(text(R.id.displayNameInput)); else controller.me(); });
        });
        findViewById(R.id.secondaryButton).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.primaryButton)).setText("Load profile");
        if (session.token().isEmpty()) unauthorized(); else withNetwork(() -> controller.me());
    }
    @Override public void success(String action, User user) {
        loaded = true;
        ((TextView) findViewById(R.id.accountText)).setText(user.accountName);
        ((EditText) findViewById(R.id.displayNameInput)).setText(user.displayName);
        ((TextView) findViewById(R.id.primaryButton)).setText("Save changes");
        if (action.equals("update")) Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show();
    }
}
