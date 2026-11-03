package com.wardrobe.atelier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wardrobe.atelier.data.LocalStore
import com.wardrobe.atelier.domain.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var name: String

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "test_${UUID.randomUUID()}.db"
        store = LocalStore(context, name)
        store.register("alice", "password123", "Alice")
    }

    @After
    fun cleanup() {
        store.close()
        context.deleteDatabase(name)
        context.getSharedPreferences("session_$name", 0).edit().clear().commit()
    }

    private fun garment() = store.saveGarment(Garment(name = "测试衬衫"))

    @Test
    fun accountsAreIsolatedForReadsAndWrites() {
        val a = garment()
        store.logout()
        store.register("bobby", "password456", "Bob")
        assertTrue(store.snapshot().garments.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.archive(a, true) }
        assertThrows(IllegalArgumentException::class.java) {
            store.saveWear(Wear(items = listOf(a)))
        }
        store.logout()
        store.login("alice", "password123")
        assertEquals(a, store.snapshot().garments.single().id)
    }

    @Test
    fun duplicatePlanConfirmationIsRejectedAndSnapshotsAreIndependent() {
        val a = garment()
        val b = store.saveGarment(Garment(name = "第二件"))
        val o = store.saveOutfit(Outfit(name = "搭配", items = listOf(a)))
        val p = store.savePlan(Plan(title = "搭配", items = listOf(a), outfitId = o))
        assertTrue(store.snapshot().wears.isEmpty())
        store.saveWear(Wear(items = listOf(a), planId = p))
        assertThrows(IllegalArgumentException::class.java) {
            store.saveWear(Wear(items = listOf(a), planId = p))
        }
        store.savePlan(Plan(p, title = "调整计划", items = listOf(b), outfitId = o))
        store.deleteOutfit(o)
        store.deletePlan(p)
        assertEquals(listOf(a), store.snapshot().wears.single().items)
    }

    @Test
    fun voidingRecountsStatisticsWithoutRevertingCare() {
        val a = garment()
        val w = store.saveWear(Wear(items = listOf(a)))
        store.setCare(a, Care.DIRTY)
        assertEquals(1, store.snapshot().wearCount(a))
        store.voidWear(w)
        val s = store.snapshot()
        assertEquals(0, s.wearCount(a))
        assertEquals(Care.DIRTY, s.garments.single().care)
        assertTrue(s.wears.single().voided)
    }

    @Test
    fun archivingPreservesBothHistories() {
        val a = garment()
        store.saveWear(Wear(items = listOf(a)))
        store.setCare(a, Care.WASHING)
        store.archive(a, true)
        val s = store.snapshot()
        assertTrue(s.active.isEmpty())
        assertEquals(1, s.wears.size)
        assertEquals(1, s.careLogs.size)
        store.archive(a, false)
        assertEquals(1, store.snapshot().active.size)
    }

    @Test
    fun careChangesOnlyLogActualTransitionsAndLocationIsIndependent() {
        val a = garment()
        store.setCare(a, Care.DIRTY)
        store.setCare(a, Care.DIRTY)
        store.renameLocation("衣柜", "宿舍衣柜")
        val s = store.snapshot()
        assertEquals(1, s.careLogs.size)
        assertEquals("宿舍衣柜", s.garments.single().location)
        assertEquals(Care.DIRTY, s.garments.single().care)
    }

    @Test
    fun datesAndCorrectionsAreValidated() {
        val a = garment()
        assertThrows(IllegalArgumentException::class.java) {
            store.saveWear(Wear(date = LocalDate.now().plusDays(1).toString(), items = listOf(a)))
        }
        val w = store.saveWear(Wear(items = listOf(a)))
        store.saveWear(
            Wear(
                w,
                date = LocalDate.now().minusDays(1).toString(),
                items = listOf(a),
                notes = "corrected",
            )
        )
        assertEquals(1, store.snapshot().wearCount(a))
        assertEquals("corrected", store.snapshot().wears.single().notes)
    }

    @Test
    fun reopeningPreservesSessionAndData() {
        val a = garment()
        store.close()
        store = LocalStore(context, name)
        assertEquals("alice", store.account()!!.username)
        assertEquals(a, store.snapshot().garments.single().id)
        store.logout()
        assertThrows(IllegalStateException::class.java) { store.snapshot() }
    }

    @Test
    fun backupRoundTripPreservesHistoryAndRejectsWrongPassword() {
        val a = garment()
        val o = store.saveOutfit(Outfit(name = "搭配", items = listOf(a)))
        val p = store.savePlan(Plan(title = "搭配", items = listOf(a), outfitId = o))
        store.saveWear(Wear(items = listOf(a), planId = p))
        store.setCare(a, Care.DIRTY)
        val file = File(context.cacheDir, "${UUID.randomUUID()}.yixu")
        val uri = Uri.fromFile(file)
        try {
            store.exportBackup(uri, "backup-pass")
            assertThrows(IllegalArgumentException::class.java) {
                store.importBackup(uri, "wrong-pass")
            }
            assertEquals(1, store.snapshot().garments.size)
            store.saveGarment(Garment(name = "待替换"))
            store.importBackup(uri, "backup-pass")
            val s = store.snapshot()
            assertEquals(1, s.garments.size)
            assertEquals(1, s.outfits.size)
            assertEquals(s.plans.single().id, s.wears.single().planId)
            assertEquals(s.garments.single().id, s.wears.single().items.single())
            assertEquals(1, s.careLogs.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun reservedDemoAccountCannotBypassPrivatePasswords() {
        assertThrows(IllegalArgumentException::class.java) {
            store.register("wardrobe_demo", "my-password", "Private")
        }
    }

    @Test
    fun photosArePrivateResizedAndIncludedInEncryptedBackup() {
        val source = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val backup = File(context.cacheDir, "${UUID.randomUUID()}.yixu")
        val invalid = File(context.cacheDir, "${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.CYAN)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var imported = ""
        var restored = ""
        try {
            imported = store.importImage(Uri.fromFile(source))
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imported, dimensions)
            assertTrue(maxOf(dimensions.outWidth, dimensions.outHeight) <= 1600)
            assertEquals("image/jpeg", dimensions.outMimeType)
            store.saveGarment(Garment(name = "带照片的外套", category = "外套", image = imported))
            store.exportBackup(Uri.fromFile(backup), "photo-backup")
            store.logout()
            store.register("bobby", "password456", "Bob")
            assertNull(store.privateImage(imported))
            assertThrows(IllegalArgumentException::class.java) {
                store.saveGarment(Garment(name = "越权图片", image = imported))
            }
            store.logout()
            store.login("alice", "password123")
            store.importBackup(Uri.fromFile(backup), "photo-backup")
            restored = store.snapshot().garments.single().image
            assertTrue(File(restored).isFile)
            assertFalse(File(imported).exists())
            assertNotNull(BitmapFactory.decodeFile(restored))
            invalid.writeText("this is not an image")
            assertThrows(IllegalArgumentException::class.java) {
                store.importImage(Uri.fromFile(invalid))
            }
            invalid.writeBytes(ByteArray(5 * 1024 * 1024 + 1))
            assertThrows(IllegalArgumentException::class.java) {
                store.importImage(Uri.fromFile(invalid))
            }
        } finally {
            source.delete()
            backup.delete()
            invalid.delete()
            if (imported.isNotEmpty()) File(imported).delete()
            if (restored.isNotEmpty()) File(restored).delete()
        }
    }
}
