package com.wardrobe.atelier

import com.wardrobe.atelier.domain.AccountRules
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountRulesTest {
    @Test fun registrationAcceptsBoundaryLengthsAndTrimsAccountAndNickname() {
        AccountRules.validateRegistration(" abc ", "p".repeat(8), " A ", "p".repeat(8))
        AccountRules.validateRegistration("a".repeat(24), "p".repeat(64), "名".repeat(20), "p".repeat(64))
    }

    @Test fun registrationRejectsMalformedAccountAndOutOfRangeFields() {
        for (username in listOf("ab", "a".repeat(25), "name@home", "姓名")) {
            assertThrows(IllegalArgumentException::class.java) {
                AccountRules.validateRegistration(username, "password1", "Alice", "password1")
            }
        }
        for (password in listOf("short", "p".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) {
                AccountRules.validateRegistration("alice", password, "Alice", password)
            }
        }
        for (nickname in listOf("   ", "名".repeat(21))) {
            assertThrows(IllegalArgumentException::class.java) {
                AccountRules.validateNickname(nickname)
            }
        }
    }

    @Test fun passwordConfirmationPreservesSpaces() {
        AccountRules.validateRegistration("alice", " pass123 ", "Alice", " pass123 ")
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validateRegistration("alice", " pass123 ", "Alice", "pass123")
        }
    }

    @Test fun passwordChangeRequiresOriginalPasswordAndMatchingConfirmation() {
        AccountRules.validatePasswordChange("oldpass12", "newpass34", "newpass34")
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validatePasswordChange("", "newpass34", "newpass34")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validatePasswordChange("oldpass12", "newpass34", "different")
        }
    }

    @Test fun loginRejectsMissingCredentials() {
        AccountRules.validateLogin(" Alice ", "password1")
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validateLogin("   ", "password1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountRules.validateLogin("alice", "")
        }
    }

    @Test fun registrationCannotClaimBuiltInWardrobeNames() {
        for (username in listOf("wardrobe_demo", " WARDROBE_LOCAL ")) {
            assertThrows(IllegalArgumentException::class.java) {
                AccountRules.validateRegistration(username, "password1", "Alice", "password1")
            }
        }
    }
}
