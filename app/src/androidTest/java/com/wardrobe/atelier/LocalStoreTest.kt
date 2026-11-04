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

    @Test
    fun quickEntryFillsOnlyHonestDefaultsAndCreatesNoActivity() {
        val id =
            store.saveQuickGarment(
                Garment(category = "连衣裙", name = "", color = "其他", season = "未标注", location = "")
            )
        val saved = store.snapshot().garments.single { it.id == id }
        assertEquals("连衣裙", saved.name)
        assertEquals("待放置", saved.location)
        assertEquals("未标注", saved.season)
        assertTrue(store.snapshot().wears.isEmpty())
        assertTrue(store.snapshot().careLogs.isEmpty())
    }

    @Test
    fun localSpaceIsStableAndDoesNotExposePasswordAccounts() {
        val aliceId = garment()
        val first = store.openLocalWardrobe()
        assertTrue(store.snapshot().garments.isEmpty())
        store.saveQuickGarment(Garment(name = "本机衣物"))
        store.logout()
        assertEquals(first.id, store.openLocalWardrobe().id)
        assertEquals("本机衣物", store.snapshot().garments.single().name)
        store.login("alice", "password123")
        assertEquals(aliceId, store.snapshot().garments.single().id)
        assertThrows(IllegalArgumentException::class.java) {
            store.register("wardrobe_local", "password123", "冒用")
        }
    }

    @Test
    fun bulkMoveIsAtomicAndKeepsHistoryUntouched() {
        val a = garment()
        store.logout()
        store.register("bobby", "password456", "Bob")
        val b = garment()
        store.login("alice", "password123")
        assertThrows(IllegalArgumentException::class.java) {
            store.moveGarments(listOf(a, b), "宿舍新柜")
        }
        assertEquals("衣柜", store.snapshot().garments.single().location)
        assertFalse("宿舍新柜" in store.snapshot().locations)
        val c = store.saveQuickGarment(Garment(name = "另一件"))
        store.moveGarments(listOf(a, c), "宿舍新柜")
        assertTrue(store.snapshot().garments.all { it.location == "宿舍新柜" })
        assertTrue(store.snapshot().wears.isEmpty())
        assertTrue(store.snapshot().careLogs.isEmpty())
    }

    @Test
    fun capsulePackingIsIsolatedPersistentAndNeverBecomesWear() {
        val a = garment()
        val b = store.saveGarment(Garment(name = "长裤", category = "下装"))
        val id = store.saveCapsule(Capsule(name = "周末", looks = listOf(listOf(a, b))))
        store.setCapsulePacked(id, a, true)
        store.close()
        store = LocalStore(context, name)
        assertEquals(listOf(a), store.snapshot().capsules.single().packed)
        assertTrue(store.snapshot().wears.isEmpty())
        assertTrue(store.snapshot().careLogs.isEmpty())
        store.logout()
        store.register("bobby", "password456", "Bob")
        assertTrue(store.snapshot().capsules.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.setCapsulePacked(id, a, true) }
        assertThrows(IllegalArgumentException::class.java) { store.deleteCapsule(id) }
        store.login("alice", "password123")
        store.resetCapsulePacked(id)
        assertTrue(store.snapshot().capsules.single().packed.isEmpty())
        store.deleteCapsule(id)
        assertEquals(2, store.snapshot().garments.size)
    }

    @Test
    fun capsuleBackupRemapsEveryGarmentAndPackedReference() {
        val a = garment()
        val b = store.saveGarment(Garment(name = "连衣裙", category = "连衣裙"))
        store.saveCapsule(
            Capsule(
                name = "轻装",
                looks = listOf(listOf(a), listOf(b)),
                packed = listOf(a),
                notes = "带雨伞",
            )
        )
        val file = File(context.cacheDir, "capsule-${UUID.randomUUID()}.yixubackup")
        try {
            store.exportBackup(Uri.fromFile(file), "backup-password")
            store.logout()
            store.register("bobby", "password456", "Bob")
            store.importBackup(Uri.fromFile(file), "backup-password")
            val data = store.snapshot()
            val capsule = data.capsules.single()
            assertEquals("带雨伞", capsule.notes)
            assertEquals(data.garments.map { it.id }.toSet(), capsule.items.toSet())
            assertTrue(capsule.items.none { it in setOf(a, b) })
            assertEquals("测试衬衫", data.pieces(capsule.packed).single().name)
        } finally {
            file.delete()
        }
    }

    @Test
    fun schemaTwoUpgradeKeepsExistingGarments() {
        val id = garment()
        store.writableDatabase.execSQL("DROP TABLE capsules")
        store.writableDatabase.version = 2
        store.close()
        store = LocalStore(context, name)
        assertEquals(id, store.snapshot().garments.single().id)
        assertTrue(store.snapshot().capsules.isEmpty())
        store.saveCapsule(Capsule(name = "升级后", looks = listOf(listOf(id))))
        assertEquals(1, store.snapshot().capsules.size)
    }

    @Test
    fun legacyBackupWithoutCapsulesStillImports() {
        garment()
        val file = File(context.cacheDir, "legacy-${UUID.randomUUID()}.yixubackup")
        try {
            store.exportBackup(Uri.fromFile(file), "backup-password")
            val json =
                org.json.JSONObject(
                    String(
                        com.wardrobe.atelier.data.BackupCipher.decrypt(
                            file.readBytes(),
                            "backup-password",
                        ),
                        Charsets.UTF_8,
                    )
                )
            json.put("schema", 1)
            json.remove("capsules")
            file.writeBytes(
                com.wardrobe.atelier.data.BackupCipher.encrypt(
                    json.toString().toByteArray(Charsets.UTF_8),
                    "backup-password",
                )
            )
            store.importBackup(Uri.fromFile(file), "backup-password")
            assertEquals("测试衬衫", store.snapshot().garments.single().name)
            assertTrue(store.snapshot().capsules.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun batchEntryRollsBackWholeBatchAndDoesNotCreateActivity() {
        assertThrows(IllegalArgumentException::class.java) {
            store.saveQuickGarments(
                listOf(Garment(name = "第一件", location = "新位置"), Garment(name = "错".repeat(41)))
            )
        }
        assertTrue(store.snapshot().garments.isEmpty())
        assertFalse("新位置" in store.snapshot().locations)
        val saved =
            store.saveQuickGarments(
                listOf(Garment(name = "上衣"), Garment(name = "长裤", category = "下装"))
            )
        assertEquals(2, saved.size)
        assertTrue(store.snapshot().wears.isEmpty())
        assertTrue(store.snapshot().careLogs.isEmpty())
    }

    @Test
    fun failedPhotoBatchCleansOnlyNewUnreferencedPrivateCopies() {
        val source = File(context.cacheDir, "batch-${UUID.randomUUID()}.png")
        val invalid = File(context.cacheDir, "invalid-${UUID.randomUUID()}.png")
        try {
            source.outputStream().use {
                Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            invalid.writeText("not an image")
            val kept = store.importImage(Uri.fromFile(source))
            store.saveGarment(Garment(name = "已保存", image = kept))
            val dir = File(kept).parentFile!!
            val before = dir.listFiles()!!.map { it.name }.toSet()
            assertThrows(IllegalArgumentException::class.java) {
                store.importImages(listOf(Uri.fromFile(source), Uri.fromFile(invalid)))
            }
            assertEquals(before, dir.listFiles()!!.map { it.name }.toSet())
            val draft = store.importImage(Uri.fromFile(source))
            store.discardDraftImages(listOf(kept, draft, source.absolutePath))
            assertTrue(File(kept).isFile)
            assertFalse(File(draft).exists())
            assertTrue(source.isFile)
            File(kept).delete()
        } finally {
            source.delete()
            invalid.delete()
        }
    }
}
