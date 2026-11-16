package com.example.wardrobemanager.model;

import android.content.Context;
import com.example.wardrobemanager.BuildConfig;
import java.net.URI;

public final class EndpointStore {
    private EndpointStore() {}
    public static String get(Context context) {
        return context.getSharedPreferences("connection", 0).getString("url", "http://10.0.2.2:8080");
    }
    public static void save(Context context, String value) {
        String clean = value.trim().replaceAll("/+$", "");
        try {
            URI uri = new URI(clean);
            boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                    || (BuildConfig.DEBUG && "http".equalsIgnoreCase(uri.getScheme()));
            if (!allowed || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPath() == null || uri.getPath().isEmpty()))
                throw new IllegalArgumentException();
        } catch (Exception error) { throw new IllegalArgumentException("Enter a server address such as http://10.0.2.2:8080"); }
        context.getSharedPreferences("connection", 0).edit().putString("url", clean).apply();
    }
}
