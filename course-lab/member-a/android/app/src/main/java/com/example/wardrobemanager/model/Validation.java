package com.example.wardrobemanager.model;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Validation {
    private Validation() {}
    public static String account(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    public static String registrationError(String account, String password, String confirmation, String name) {
        if (!account(account).matches("[a-z0-9_]{3,32}"))
            return "Use 3–32 letters, numbers or underscores for your account.";
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) return "Use a password of 8–72 UTF-8 bytes.";
        if (!password.equals(confirmation)) return "The passwords do not match.";
        if (name.trim().isEmpty() || name.trim().length() > 40) return "Enter a display name of 1–40 characters.";
        return null;
    }
}
