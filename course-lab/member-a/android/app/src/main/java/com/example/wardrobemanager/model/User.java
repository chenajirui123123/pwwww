package com.example.wardrobemanager.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class User {
    public final String id, accountName, displayName;
    public User(String id, String accountName, String displayName) {
        this.id = id; this.accountName = accountName; this.displayName = displayName;
    }
    public static User fromJson(JSONObject value) throws JSONException {
        return new User(value.getString("id"), value.getString("accountName"), value.getString("displayName"));
    }
}
