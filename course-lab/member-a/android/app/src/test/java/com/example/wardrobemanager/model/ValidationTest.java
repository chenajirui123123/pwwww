package com.example.wardrobemanager.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValidationTest {
    @Test public void normalizesAccountButNotPassword() {
        assertEquals("chen_01", Validation.account("  Chen_01 "));
        assertNull(Validation.registrationError("Chen_01", "pass word ", "pass word ", "Chen"));
        assertNotNull(Validation.registrationError("Chen_01", "pass word ", "pass word", "Chen"));
    }
    @Test public void rejectsByteLengthBeyondBcryptLimit() {
        String multi = "衣".repeat(25);
        assertNotNull(Validation.registrationError("chen", multi, multi, "Chen"));
        assertNull(Validation.registrationError("chen", "衣".repeat(24), "衣".repeat(24), "Chen"));
    }
    @Test public void rejectsInvalidAccountAndBlankName() {
        assertNotNull(Validation.registrationError("a/b", "password1", "password1", "Chen"));
        assertNotNull(Validation.registrationError("chen", "password1", "password1", "  "));
    }
}
