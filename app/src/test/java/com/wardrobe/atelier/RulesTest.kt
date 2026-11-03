package com.wardrobe.atelier

import com.wardrobe.atelier.data.BackupCipher
import com.wardrobe.atelier.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun datesAndSelectionMustBeValid() {
        Rules.wear("2026-10-31", listOf(1, 2), today)
        listOf("2026-11-02", "2026-02-30", "invalid").forEach { d ->
            assertThrows(IllegalArgumentException::class.java) { Rules.wear(d, listOf(1), today) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Rules.wear(today.toString(), emptyList(), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Rules.wear(today.toString(), listOf(1, 1), today)
        }
    }

    @Test
    fun actualRecordsDriveCountsNotPlansOrVoidedRecords() {
        val w =
            Wardrobe(
                garments = listOf(Garment(1, "Test", created = "2026-10-01")),
                plans = listOf(Plan(items = listOf(1))),
                wears =
                    listOf(
                        Wear(1, "2026-10-29", listOf(1)),
                        Wear(2, "2026-10-30", listOf(1)),
                        Wear(3, "2026-10-31", listOf(1), voided = true),
                    ),
            )
        assertEquals(2, w.wearCount(1))
        assertEquals(2L, w.idleDays(w.garments.first(), today))
    }

    @Test
    fun neverWornCountsFromCreation() {
        val g = Garment(1, "Test", created = "2026-10-01")
        assertEquals(31L, Wardrobe().idleDays(g, today))
    }

    @Test
    fun invalidPricesAndStyleAreRejected() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 1000000.0).forEach { p ->
            assertThrows(IllegalArgumentException::class.java) {
                Rules.garment(Garment(name = "衣物", price = p))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Rules.garment(Garment(name = "衣物", category = "上装", style = "半裙"))
        }
        Rules.garment(Garment(name = "半裙", category = "下装", style = "半裙"))
    }

    @Test
    fun backupsAreAuthenticatedAndUseFreshSalt() {
        val data = "My private wardrobe".toByteArray()
        val a = BackupCipher.encrypt(data, "test-password")
        val b = BackupCipher.encrypt(data, "test-password")
        assertFalse(a.contentEquals(b))
        assertArrayEquals(data, BackupCipher.decrypt(a, "test-password"))
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(a, "wrong-password")
        }
        a[a.lastIndex] = (a.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCipher.decrypt(a, "test-password")
        }
    }
}
