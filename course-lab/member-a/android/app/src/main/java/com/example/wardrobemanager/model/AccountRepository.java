package com.example.wardrobemanager.model;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Blocking data access. AccountController always invokes this off the UI thread. */
public final class AccountRepository {
    private final String baseUrl;
    public AccountRepository(String baseUrl) { this.baseUrl = baseUrl; }
    public static final class ApiException extends Exception {
        public final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
    public JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream stream = connection.getOutputStream()) { stream.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            String text = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            JSONObject json;
            try { json = text.isEmpty() ? new JSONObject() : new JSONObject(text); }
            catch (Exception malformed) { throw new ApiException(status, "The server returned an unexpected response."); }
            if (status < 200 || status >= 300) throw new ApiException(status, json.optString("message", "The request could not be completed."));
            return json;
        } finally { connection.disconnect(); }
    }
    private String read(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 1024 * 1024) throw new Exception("Response too large");
            }
            return output.toString("UTF-8");
        }
    }
}
