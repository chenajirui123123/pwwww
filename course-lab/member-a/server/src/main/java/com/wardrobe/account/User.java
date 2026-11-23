package com.wardrobe.account;

/** Public account data. Password hashes never appear in an API response. */
public record User(String id, String accountName, String displayName) {}
