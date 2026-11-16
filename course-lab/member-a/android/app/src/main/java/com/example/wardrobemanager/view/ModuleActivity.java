package com.example.wardrobemanager.view;

import android.os.Bundle;
import android.widget.TextView;
import com.example.wardrobemanager.R;

/** Replace these routes with the real screens supplied by members B–E. */
public final class ModuleActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); page(R.layout.activity_module);
        if (session.token().isEmpty()) { unauthorized(); return; }
        ((TextView) findViewById(R.id.titleText)).setText(getIntent().getStringExtra("title"));
        ((TextView) findViewById(R.id.descriptionText)).setText(getIntent().getStringExtra("description")
                + "\n\nThis screen is reserved for member " + getIntent().getStringExtra("owner")
                + ". Its business functions have not been connected yet.");
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }
}
