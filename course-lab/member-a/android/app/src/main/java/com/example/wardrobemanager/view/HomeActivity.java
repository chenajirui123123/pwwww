package com.example.wardrobemanager.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.example.wardrobemanager.R;
import com.example.wardrobemanager.model.User;

public final class HomeActivity extends BaseActivity {
    private boolean loaded;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); page(R.layout.activity_home);
        findViewById(R.id.profileButton).setOnClickListener(v -> go(ProfileActivity.class));
        module(R.id.wardrobeCard, "My wardrobe", "B", "Add, find and organise your clothes.");
        module(R.id.outfitsCard, "Outfits & calendar", "C", "Create outfits and plan what to wear.");
        module(R.id.careCard, "Wear & care", "D", "Record actual wear and manage garment care.");
        module(R.id.statisticsCard, "Usage statistics", "E", "View usage based on valid actual wear records.");
        findViewById(R.id.logoutButton).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Sign out?").setMessage("You will return to the sign-in screen.")
                .setNegativeButton("Cancel", null).setPositiveButton("Sign out", (dialog, which) -> withNetwork(() -> controller.logout())).show());
    }
    private void module(int id, String title, String owner, String description) {
        findViewById(id).setOnClickListener(v -> startActivity(new Intent(this, ModuleActivity.class)
                .putExtra("title", title).putExtra("owner", owner).putExtra("description", description)));
    }
    @Override protected void onResume() {
        super.onResume();
        if (session.token().isEmpty()) { unauthorized(); return; }
        withNetwork(() -> controller.me());
    }
    @Override public void busy(boolean value) {
        super.busy(value);
        for (int id : new int[]{R.id.profileButton, R.id.wardrobeCard, R.id.outfitsCard, R.id.careCard, R.id.statisticsCard})
            findViewById(id).setEnabled(!value && loaded);
        findViewById(R.id.logoutButton).setEnabled(!value);
    }
    @Override public void success(String action, User user) {
        if (action.equals("logout")) { resetTo(LoginActivity.class); return; }
        loaded = true;
        ((TextView) findViewById(R.id.greetingText)).setText("Hello, " + user.displayName);
        busy(false);
    }
    @Override public void error(String message) {
        super.error(message);
        // A failed session check must not reveal a previous account's content.
        if (!loaded) new AlertDialog.Builder(this).setTitle("Connection unavailable")
                .setMessage(message).setCancelable(false)
                .setPositiveButton("Retry", (d, w) -> withNetwork(() -> controller.me()))
                .setNegativeButton("Back to sign in", (d, w) -> { session.clear(); resetTo(LoginActivity.class); }).show();
    }
}
