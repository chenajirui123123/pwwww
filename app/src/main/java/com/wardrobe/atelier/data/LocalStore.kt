package com.wardrobe.atelier.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.wardrobe.atelier.domain.*
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray

/** All business reads and writes are scoped to the authenticated local account. */
class LocalStore(private val context: Context, dbName: String = "wardrobe.db") :
    SQLiteOpenHelper(context, dbName, null, 3) {
    private val prefs = context.getSharedPreferences("session_$dbName", Context.MODE_PRIVATE)
    private var signedIn: Long = prefs.getLong("owner", 0)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, nickname TEXT NOT NULL, salt TEXT NOT NULL, hash TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE garments(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, name TEXT, category TEXT, color TEXT, season TEXT, location TEXT, brand TEXT, price REAL, notes TEXT, image TEXT, care TEXT, archived INTEGER, created TEXT, style TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE outfits(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, name TEXT, occasion TEXT, notes TEXT, items TEXT)"
        )
        db.execSQL(
            "CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, date TEXT, title TEXT, items TEXT, outfitId INTEGER, notes TEXT)"
        )
        db.execSQL(
            "CREATE TABLE wears(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, date TEXT, items TEXT, notes TEXT, planId INTEGER, voided INTEGER, UNIQUE(owner,planId))"
        )
        db.execSQL(
            "CREATE TABLE care_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, garmentId INTEGER, beforeStatus TEXT, afterStatus TEXT, time TEXT)"
        )
        db.execSQL(
            "CREATE TABLE locations(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, name TEXT, UNIQUE(owner,name))"
        )
        listOf("garments", "outfits", "plans", "wears", "care_logs", "locations").forEach {
            db.execSQL("CREATE INDEX idx_${it}_owner ON $it(owner)")
        }
        createCapsules(db)
    }

    private fun createCapsules(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE capsules(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER NOT NULL, name TEXT NOT NULL, season TEXT NOT NULL, looks TEXT NOT NULL, packed TEXT NOT NULL, notes TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_capsules_owner ON capsules(owner)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2)
            db.execSQL("ALTER TABLE garments ADD COLUMN style TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 3) createCapsules(db)
    }

    private fun values(vararg pairs: Pair<String, Any?>) =
        ContentValues().apply {
            pairs.forEach { (k, v) ->
                when (v) {
                    null -> putNull(k)
                    is Long -> put(k, v)
                    is Int -> put(k, v)
                    is Double -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }

    private fun <T> query(
        table: String,
        where: String? = null,
        args: Array<String>? = null,
        order: String? = null,
        map: (Cursor) -> T,
    ): List<T> =
        readableDatabase.query(table, null, where, args, null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(map(c)) }
        }

    private fun Cursor.s(k: String) = getString(getColumnIndexOrThrow(k)) ?: ""

    private fun Cursor.l(k: String) = getLong(getColumnIndexOrThrow(k))

    private fun ids(text: String): List<Long> =
        JSONArray(text).let { a -> (0 until a.length()).map { a.getLong(it) } }

    private fun json(ids: List<Long>) = JSONArray(ids).toString()

    private fun owner(): Long {
        check(signedIn > 0 && account() != null) { "请先登录" }
        return signedIn
    }

    private fun <T> owned(table: String, order: String? = null, map: (Cursor) -> T) =
        query(table, "owner=?", arrayOf(owner().toString()), order, map)

    fun account(): Account? =
        if (signedIn == 0L) null
        else
            query("accounts", "id=?", arrayOf(signedIn.toString())) {
                    Account(it.l("id"), it.s("username"), it.s("nickname"))
                }
                .firstOrNull()

    private fun password(pass: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120000, 256))
            .encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun signIn(id: Long) {
        signedIn = id
        check(prefs.edit().putLong("owner", id).commit()) { "无法保存登录状态" }
    }

    fun register(username: String, pass: String, nickname: String): Account {
        require(username.trim().lowercase() !in setOf("wardrobe_demo", "wardrobe_local")) {
            "此账号名称为内置衣橱保留，请选择其他名称"
        }
        return createAccount(username, pass, nickname)
    }

    /** A local space is deliberately password-free; password-protected accounts stay separate. */
    fun openLocalWardrobe(): Account {
        val id =
            query("accounts", "username=?", arrayOf("wardrobe_local")) { it.l("id") }.firstOrNull()
        if (id != null) signIn(id)
        else createAccount("wardrobe_local", UUID.randomUUID().toString(), "我的本机衣橱")
        return account()!!
    }

    fun saveQuickGarment(g: Garment): Long {
        val title =
            g.name.trim().ifBlank {
                (if (g.color == "其他") "" else g.color) + g.style.ifBlank { g.category }
            }
        return saveGarment(g.copy(name = title, location = g.location.trim().ifBlank { "待放置" }))
    }

    fun saveQuickGarments(garments: List<Garment>): List<Long> {
        require(garments.size in 1..20 && garments.all { it.id == 0L }) { "每次可新增 1–20 件衣物" }
        writableDatabase.beginTransaction()
        try {
            val ids = garments.map { saveQuickGarment(it) }
            writableDatabase.setTransactionSuccessful()
            return ids
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun importImages(uris: List<Uri>): List<String> {
        val unique = uris.distinct()
        require(unique.size in 1..20) { "一次请选择 1–20 张图片" }
        val made = mutableListOf<String>()
        try {
            unique.forEachIndexed { index, uri ->
                try {
                    made += importImage(uri)
                } catch (e: Exception) {
                    throw IllegalArgumentException("第 ${index + 1} 张图片：${e.message ?: "无法读取"}", e)
                }
            }
            return made
        } catch (e: Exception) {
            discardDraftImages(made)
            throw e
        }
    }

    /** Only a current owner's unreferenced private imports can be removed. */
    fun discardDraftImages(paths: List<String>) {
        val referenced = owned("garments", map = ::garment).map { it.image }.toSet()
        paths.distinct().filter { it !in referenced }.forEach { privateImage(it)?.delete() }
    }

    fun moveGarments(ids: List<Long>, location: String) {
        require(ids.isNotEmpty() && ids.size == ids.distinct().size) { "请选择需要移动的衣物" }
        require(location.trim().length in 1..40) { "位置名称应为 1–40 个字" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { requireOwned("garments", it) }
            addLocation(location)
            ids.forEach { save("garments", it, values("location" to location.trim())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createAccount(username: String, pass: String, nickname: String): Account {
        val u = username.trim().lowercase()
        require(u.matches(Regex("[a-z0-9_]{3,24}"))) { "账号需为 3–24 位字母、数字或下划线" }
        require(pass.length in 8..64) { "密码需为 8–64 位字符" }
        require(nickname.trim().length in 1..20) { "昵称需为 1–20 个字" }
        require(query("accounts", "username=?", arrayOf(u)) { it.l("id") }.isEmpty()) {
            "这个账号已存在，请直接登录"
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id =
            writableDatabase.insertOrThrow(
                "accounts",
                null,
                values(
                    "username" to u,
                    "nickname" to nickname.trim(),
                    "salt" to b64(salt),
                    "hash" to b64(password(pass, salt)),
                ),
            )
        signIn(id)
        listOf("衣柜", "鞋架", "收纳箱").forEach { addLocation(it) }
        return account()!!
    }

    fun login(username: String, pass: String): Account {
        val entry =
            query("accounts", "username=?", arrayOf(username.trim().lowercase())) {
                    Triple(it.l("id"), it.s("salt"), it.s("hash"))
                }
                .firstOrNull()
        require(
            entry != null &&
                MessageDigest.isEqual(
                    password(pass, Base64.decode(entry.second, Base64.NO_WRAP)),
                    Base64.decode(entry.third, Base64.NO_WRAP),
                )
        ) {
            "账号或密码不正确"
        }
        signIn(entry!!.first)
        return account()!!
    }

    fun logout() {
        signedIn = 0
        check(prefs.edit().remove("owner").commit()) { "无法保存退出状态，请重试" }
    }

    fun rename(nickname: String) {
        require(nickname.trim().length in 1..20) { "昵称需为 1–20 个字" }
        writableDatabase.update(
            "accounts",
            values("nickname" to nickname.trim()),
            "id=?",
            arrayOf(owner().toString()),
        )
    }

    fun changePassword(old: String, new: String) {
        val a = account() ?: error("请先登录")
        require(new.length in 8..64) { "新密码需为 8–64 位字符" }
        login(a.username, old)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        writableDatabase.update(
            "accounts",
            values("salt" to b64(salt), "hash" to b64(password(new, salt))),
            "id=?",
            arrayOf(a.id.toString()),
        )
    }

    private fun garment(c: Cursor) =
        Garment(
            c.l("id"),
            c.s("name"),
            c.s("category"),
            c.s("color"),
            c.s("season"),
            c.s("location"),
            c.s("brand"),
            c.getDouble(c.getColumnIndexOrThrow("price")),
            c.s("notes"),
            c.s("image"),
            Care.valueOf(c.s("care")),
            c.l("archived") == 1L,
            c.s("created"),
            c.s("style"),
        )

    fun snapshot() =
        Wardrobe(
            owned("garments", "id DESC", ::garment),
            owned("outfits", "id DESC") {
                Outfit(
                    it.l("id"),
                    it.s("name"),
                    it.s("occasion"),
                    it.s("notes"),
                    ids(it.s("items")),
                )
            },
            owned("plans", "date DESC, id DESC") {
                Plan(
                    it.l("id"),
                    it.s("date"),
                    it.s("title"),
                    ids(it.s("items")),
                    it.l("outfitId"),
                    it.s("notes"),
                )
            },
            owned("wears", "date DESC, id DESC") {
                Wear(
                    it.l("id"),
                    it.s("date"),
                    ids(it.s("items")),
                    it.s("notes"),
                    if (it.isNull(it.getColumnIndexOrThrow("planId"))) null else it.l("planId"),
                    it.l("voided") == 1L,
                )
            },
            owned("care_logs", "id DESC") {
                CareLog(
                    it.l("id"),
                    it.l("garmentId"),
                    Care.valueOf(it.s("beforeStatus")),
                    Care.valueOf(it.s("afterStatus")),
                    it.s("time"),
                )
            },
            owned("locations", "id ASC") { it.s("name") },
            owned("capsules", "id DESC") {
                Capsule(
                    it.l("id"),
                    it.s("name"),
                    it.s("season"),
                    looks(it.s("looks")),
                    ids(it.s("packed")),
                    it.s("notes"),
                )
            },
        )

    private fun looks(text: String): List<List<Long>> =
        JSONArray(text).let { a -> (0 until a.length()).map { ids(a.getJSONArray(it).toString()) } }

    private fun looksJson(value: List<List<Long>>) =
        JSONArray().apply { value.forEach { put(JSONArray(it)) } }.toString()

    private fun validateCapsule(c: Capsule) {
        require(c.name.trim().length in 1..40 && c.notes.length <= 500) { "请填写行李胶囊名称，备注最多 500 字" }
        require(c.season in listOf("不限", "春秋", "夏季", "冬季")) { "季节无效" }
        require(
            c.looks.size in 1..7 && c.looks.all { it.size in 1..8 && it.distinct().size == it.size }
        ) {
            "每份行李胶囊可保存 1–7 套搭配"
        }
        require(c.packed.distinct().size == c.packed.size && c.packed.all { it in c.items }) {
            "清单勾选关联无效"
        }
    }

    fun saveCapsule(c: Capsule): Long {
        validateCapsule(c)
        requireItems(c.items, c.id == 0L)
        return save(
            "capsules",
            c.id,
            values(
                "name" to c.name.trim(),
                "season" to c.season,
                "looks" to looksJson(c.looks),
                "packed" to json(c.packed),
                "notes" to c.notes,
            ),
        )
    }

    fun setCapsulePacked(id: Long, garmentId: Long, checked: Boolean) {
        requireOwned("capsules", id)
        val c =
            owned("capsules") {
                    Capsule(
                        it.l("id"),
                        it.s("name"),
                        it.s("season"),
                        looks(it.s("looks")),
                        ids(it.s("packed")),
                        it.s("notes"),
                    )
                }
                .first { it.id == id }
        require(garmentId in c.items) { "这件衣物不在当前行李清单中" }
        save(
            "capsules",
            id,
            values(
                "packed" to
                    json(if (checked) (c.packed + garmentId).distinct() else c.packed - garmentId)
            ),
        )
    }

    fun resetCapsulePacked(id: Long) {
        requireOwned("capsules", id)
        save("capsules", id, values("packed" to "[]"))
    }

    fun deleteCapsule(id: Long) {
        requireOwned("capsules", id)
        writableDatabase.delete(
            "capsules",
            "id=? AND owner=?",
            arrayOf(id.toString(), owner().toString()),
        )
    }

    private fun requireOwned(table: String, id: Long) {
        require(
            query(table, "id=? AND owner=?", arrayOf(id.toString(), owner().toString())) {
                    it.l("id")
                }
                .isNotEmpty()
        ) {
            "记录不存在或无权访问"
        }
    }

    private fun requireItems(items: List<Long>, active: Boolean = false) {
        require(items.isNotEmpty()) { "请至少选择一件衣物" }
        require(items.size == items.distinct().size) { "衣物不能重复" }
        items.forEach { id ->
            val g =
                query(
                        "garments",
                        "id=? AND owner=?",
                        arrayOf(id.toString(), owner().toString()),
                        map = ::garment,
                    )
                    .firstOrNull()
            require(g != null && (!active || !g.archived)) { "选中的衣物不存在或已归档" }
        }
    }

    private fun save(table: String, id: Long, v: ContentValues): Long {
        v.put("owner", owner())
        if (id == 0L) return writableDatabase.insertOrThrow(table, null, v)
        requireOwned(table, id)
        check(
            writableDatabase.update(
                table,
                v,
                "id=? AND owner=?",
                arrayOf(id.toString(), owner().toString()),
            ) == 1
        ) {
            "保存失败，请重试"
        }
        return id
    }

    fun saveGarment(g: Garment): Long {
        Rules.garment(g)
        if (g.id != 0L) requireOwned("garments", g.id)
        if (g.image.isNotEmpty()) require(privateImage(g.image) != null) { "图片文件无效，请重新选择" }
        addLocation(g.location.trim())
        val v =
            values(
                "name" to g.name.trim(),
                "category" to g.category,
                "color" to g.color,
                "season" to g.season,
                "location" to g.location.trim(),
                "brand" to g.brand.trim(),
                "price" to g.price,
                "notes" to g.notes,
                "image" to g.image,
                "style" to g.style,
            )
        if (g.id == 0L) {
            v.put("care", Care.READY.name)
            v.put("archived", 0)
            v.put("created", LocalDate.now().toString())
        }
        return save("garments", g.id, v)
    }

    fun archive(id: Long, archived: Boolean) {
        requireOwned("garments", id)
        save("garments", id, values("archived" to if (archived) 1 else 0))
    }

    fun saveOutfit(o: Outfit): Long {
        require(o.name.trim().length in 1..40) { "请填写搭配名称，最多 40 个字" }
        require(o.notes.length <= 500) { "备注最多 500 个字" }
        requireItems(o.items, true)
        return save(
            "outfits",
            o.id,
            values(
                "name" to o.name.trim(),
                "occasion" to o.occasion,
                "notes" to o.notes,
                "items" to json(o.items),
            ),
        )
    }

    fun deleteOutfit(id: Long) {
        requireOwned("outfits", id)
        writableDatabase.delete(
            "outfits",
            "id=? AND owner=?",
            arrayOf(id.toString(), owner().toString()),
        )
    }

    fun savePlan(p: Plan): Long {
        require(runCatching { LocalDate.parse(p.date) }.isSuccess) { "请选择有效日期" }
        require(p.title.trim().isNotEmpty()) { "请选择搭配" }
        requireItems(p.items, true)
        return save(
            "plans",
            p.id,
            values(
                "date" to p.date,
                "title" to p.title,
                "items" to json(p.items),
                "outfitId" to p.outfitId,
                "notes" to p.notes.take(500),
            ),
        )
    }

    fun deletePlan(id: Long) {
        requireOwned("plans", id)
        writableDatabase.delete(
            "plans",
            "id=? AND owner=?",
            arrayOf(id.toString(), owner().toString()),
        )
    }

    fun saveWear(w: Wear): Long {
        Rules.wear(w.date, w.items)
        require(w.notes.length <= 500) { "备注最多 500 个字" }
        requireItems(w.items)
        if (w.id != 0L)
            require(
                query("wears", "id=? AND owner=?", arrayOf(w.id.toString(), owner().toString())) {
                        it.l("voided")
                    }
                    .firstOrNull() == 0L
            ) {
                "已撤销的记录不能编辑"
            }
        val v = values("date" to w.date, "items" to json(w.items), "notes" to w.notes)
        if (w.id == 0L) {
            v.put("voided", 0)
            if (w.planId != null) {
                requireOwned("plans", w.planId)
                require(
                    query(
                            "wears",
                            "owner=? AND planId=?",
                            arrayOf(owner().toString(), w.planId.toString()),
                        ) {
                            it.l("id")
                        }
                        .isEmpty()
                ) {
                    "这个计划已经确认过，请在穿着历史中查看"
                }
                v.put("planId", w.planId)
            }
        }
        return save("wears", w.id, v)
    }

    fun voidWear(id: Long) {
        requireOwned("wears", id)
        save("wears", id, values("voided" to 1))
    }

    fun setCare(id: Long, state: Care) {
        val g =
            query(
                    "garments",
                    "id=? AND owner=?",
                    arrayOf(id.toString(), owner().toString()),
                    map = ::garment,
                )
                .firstOrNull() ?: error("衣物不存在")
        require(!g.archived) { "请先恢复已归档的衣物" }
        if (g.care == state) return
        writableDatabase.beginTransaction()
        try {
            save("garments", id, values("care" to state.name))
            save(
                "care_logs",
                0,
                values(
                    "garmentId" to id,
                    "beforeStatus" to g.care.name,
                    "afterStatus" to state.name,
                    "time" to LocalDateTime.now().toString(),
                ),
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun addLocation(name: String) {
        require(name.trim().length in 1..40) { "位置名称应为 1–40 个字" }
        writableDatabase.insertWithOnConflict(
            "locations",
            null,
            values("owner" to owner(), "name" to name.trim()),
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun renameLocation(old: String, new: String) {
        require(new.trim().length in 1..40) { "位置名称应为 1–40 个字" }
        writableDatabase.beginTransaction()
        try {
            addLocation(new)
            writableDatabase.update(
                "garments",
                values("location" to new.trim()),
                "owner=? AND location=?",
                arrayOf(owner().toString(), old),
            )
            if (old != new.trim())
                writableDatabase.delete(
                    "locations",
                    "owner=? AND name=?",
                    arrayOf(owner().toString(), old),
                )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun deleteLocation(name: String) {
        require(owned("garments", map = ::garment).none { it.location == name }) {
            "此位置仍有衣物，请先移动这些衣物"
        }
        writableDatabase.delete(
            "locations",
            "owner=? AND name=?",
            arrayOf(owner().toString(), name),
        )
    }

    fun privateImage(path: String): File? {
        val dir = File(context.filesDir, "photos/${owner()}").canonicalFile
        val file = File(path).canonicalFile
        return file.takeIf { it.parentFile == dir && it.isFile }
    }

    fun importImage(uri: Uri): String {
        owner()
        val mime = context.contentResolver.getType(uri)
        require(mime == null || mime in listOf("image/jpeg", "image/png", "image/webp")) {
            "请选择 JPG、PNG 或 WebP 图片"
        }
        val data =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    count += n
                    require(count <= 5 * 1024 * 1024) { "图片不能超过 5 MB" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: error("无法读取这张图片，请重新选择")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        require(
            bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outMimeType in listOf("image/jpeg", "image/png", "image/webp")
        ) {
            "图片文件损坏或格式不支持"
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: error("无法解码图片")
        val orientation =
            runCatching {
                    androidx.exifinterface.media
                        .ExifInterface(java.io.ByteArrayInputStream(data))
                        .getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            1,
                        )
                }
                .getOrDefault(1)
        val matrix =
            android.graphics.Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    6 -> setRotate(90f)
                    7 -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    8 -> setRotate(-90f)
                }
            }
        val oriented =
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val bmp = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(oriented, 0f, 0f, null)
        }
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        val dir = File(context.filesDir, "photos/${owner()}").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use {
                check(bmp.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "图片保存失败" }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            bmp.recycle()
        }
        return file.absolutePath
    }

    fun demo(): Account {
        val a =
            query("accounts", "username=?", arrayOf("wardrobe_demo")) {
                    Account(it.l("id"), it.s("username"), it.s("nickname"))
                }
                .firstOrNull()
        if (a != null) {
            signIn(a.id)
            return a
        }
        createAccount("wardrobe_demo", UUID.randomUUID().toString(), "衣序体验官")
        writableDatabase.beginTransaction()
        try {
            val items =
                listOf(
                        Garment(
                            name = "奶油白基础衬衫",
                            color = "米白",
                            season = "春秋",
                            brand = "EVERYDAY",
                            price = 199.0,
                        ),
                        Garment(name = "雾蓝直筒长裤", category = "下装", color = "蓝色", price = 269.0),
                        Garment(
                            name = "鼠尾草轻量外套",
                            category = "外套",
                            color = "绿色",
                            season = "春秋",
                            price = 399.0,
                        ),
                        Garment(
                            name = "极简白色运动鞋",
                            category = "鞋履",
                            color = "米白",
                            location = "鞋架",
                            price = 329.0,
                        ),
                        Garment(name = "炭黑通勤托特", category = "包袋", color = "黑色", price = 159.0),
                        Garment(
                            name = "柔粉针织上衣",
                            style = "针织",
                            category = "上装",
                            color = "粉色",
                            season = "冬季",
                            price = 229.0,
                        ),
                        Garment(
                            name = "深灰百褶半裙",
                            style = "半裙",
                            category = "下装",
                            color = "灰色",
                            price = 189.0,
                        ),
                        Garment(
                            name = "暖棕长款风衣",
                            style = "风衣",
                            category = "外套",
                            color = "棕色",
                            season = "春秋",
                            price = 599.0,
                        ),
                        Garment(
                            name = "雾粉系带连衣裙",
                            style = "连衣裙",
                            category = "连衣裙",
                            color = "粉色",
                            season = "夏季",
                            price = 329.0,
                        ),
                        Garment(
                            name = "炭黑低跟单鞋",
                            style = "低跟鞋",
                            category = "鞋履",
                            color = "黑色",
                            location = "鞋架",
                            price = 259.0,
                        ),
                        Garment(
                            name = "白色圆领T恤",
                            style = "T恤",
                            color = "米白",
                            season = "夏季",
                            price = 89.0,
                        ),
                        Garment(
                            name = "灰色连帽卫衣",
                            style = "卫衣",
                            color = "灰色",
                            season = "春秋",
                            price = 229.0,
                        ),
                        Garment(
                            name = "炭灰修身西装",
                            style = "西装",
                            category = "外套",
                            color = "灰色",
                            season = "春秋",
                            price = 499.0,
                        ),
                        Garment(
                            name = "奶油针织围巾",
                            style = "围巾",
                            category = "配饰",
                            color = "米白",
                            season = "冬季",
                            location = "收纳箱",
                            price = 99.0,
                        ),
                    )
                    .map { saveGarment(it) }
            val o1 =
                saveOutfit(
                    Outfit(
                        name = "清爽通勤",
                        occasion = "通勤",
                        notes = "轻盈层次，留一点呼吸感。",
                        items = listOf(items[0], items[1], items[3], items[4]),
                    )
                )
            val o2 =
                saveOutfit(
                    Outfit(
                        name = "周末漫游",
                        occasion = "休闲",
                        items = listOf(items[0], items[2], items[1], items[3]),
                    )
                )
            saveOutfit(
                Outfit(
                    name = "温柔的秋日",
                    occasion = "约会",
                    items = listOf(items[5], items[6], items[7]),
                )
            )
            saveOutfit(
                Outfit(
                    name = "午后的小约会",
                    occasion = "约会",
                    items = listOf(items[8], items[9], items[4]),
                )
            )
            saveOutfit(
                Outfit(
                    name = "校园轻松感",
                    occasion = "休闲",
                    items = listOf(items[10], items[11], items[1], items[3]),
                )
            )
            val today = LocalDate.now()
            savePlan(
                Plan(
                    date = today.toString(),
                    title = "清爽通勤",
                    items = listOf(items[0], items[1], items[3], items[4]),
                    outfitId = o1,
                )
            )
            savePlan(
                Plan(
                    date = today.plusDays(2).toString(),
                    title = "周末漫游",
                    items = listOf(items[0], items[2], items[1], items[3]),
                    outfitId = o2,
                )
            )
            (1..6).forEach { day ->
                saveWear(
                    Wear(
                        date = today.minusDays(day.toLong()).toString(),
                        items =
                            if (day % 2 == 0) listOf(items[0], items[1], items[3])
                            else listOf(items[5], items[6]),
                        notes = "示例穿着记录",
                    )
                )
            }
            setCare(items[5], Care.DIRTY)
            setCare(items[6], Care.WASHING)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return account()!!
    }

    fun exportBackup(uri: Uri, passphrase: String) {
        require(passphrase.length >= 8) { "备份口令至少 8 位，请妥善保存" }
        val root =
            org.json
                .JSONObject()
                .put("schema", 2)
                .put("app", "YIXU")
                .put("created", LocalDateTime.now().toString())
        val tables =
            listOf("garments", "outfits", "plans", "wears", "care_logs", "locations", "capsules")
        tables.forEach { table ->
            val a = JSONArray()
            owned(table) { c ->
                val o = org.json.JSONObject()
                c.columnNames
                    .filter { it != "owner" }
                    .forEach { key ->
                        val i = c.getColumnIndexOrThrow(key)
                        o.put(
                            key,
                            when (c.getType(i)) {
                                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                Cursor.FIELD_TYPE_NULL -> org.json.JSONObject.NULL
                                else -> c.getString(i)
                            },
                        )
                    }
                if (table == "garments") {
                    val path = o.optString("image")
                    o.put(
                        "photo",
                        if (path.isBlank()) ""
                        else privateImage(path)?.let { b64(it.readBytes()) } ?: "",
                    )
                    o.put("image", "")
                }
                a.put(o)
            }
            root.put(table, a)
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 128 * 1024 * 1024) { "备份超过 128 MB，请减少大图后再试" }
        val encrypted = BackupCipher.encrypt(bytes, passphrase)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
            ?: error("无法写入备份文件")
    }

    fun importBackup(uri: Uri, passphrase: String) {
        val encrypted =
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 129 * 1024 * 1024) { "备份文件过大" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: error("无法读取备份")
        val bytes = BackupCipher.decrypt(encrypted, passphrase)
        val root =
            runCatching { org.json.JSONObject(String(bytes, Charsets.UTF_8)) }
                .getOrElse { error("备份内容无法识别") }
        require(root.optInt("schema") in 1..2 && root.optString("app") == "YIXU") { "不支持的备份格式" }
        val tables =
            listOf("garments", "outfits", "plans", "wears", "care_logs", "locations", "capsules")
        val rows =
            tables.associateWith { t ->
                val a =
                    if (t == "capsules" && root.optInt("schema") == 1) JSONArray()
                    else root.getJSONArray(t)
                require(a.length() <= 20000) { "备份记录数量过多" }
                (0 until a.length()).map { a.getJSONObject(it) }
            }
        rows.forEach { (_, rs) ->
            require(
                rs.map { it.getLong("id") }.distinct().size == rs.size &&
                    rs.all { it.getLong("id") > 0 }
            ) {
                "备份记录编号无效"
            }
        }
        val garments = rows.getValue("garments")
        val ids = garments.map { it.getLong("id") }.toSet()
        garments.forEach { o ->
            Rules.garment(
                Garment(
                    name = o.getString("name"),
                    category = o.getString("category"),
                    style = o.optString("style"),
                    color = o.getString("color"),
                    season = o.getString("season"),
                    location = o.getString("location"),
                    price = o.getDouble("price"),
                    brand = o.getString("brand"),
                    notes = o.getString("notes"),
                )
            )
            Care.valueOf(o.getString("care"))
            LocalDate.parse(o.getString("created"))
        }
        listOf("outfits", "plans", "wears").forEach { t ->
            rows.getValue(t).forEach { o ->
                val selected = ids(o.getString("items"))
                require(
                    selected.isNotEmpty() &&
                        selected.distinct().size == selected.size &&
                        selected.all { it in ids }
                ) {
                    "备份的衣物关联无效"
                }
                if (t != "outfits") LocalDate.parse(o.getString("date"))
            }
        }
        rows.getValue("capsules").forEach { o ->
            val capsule =
                Capsule(
                    name = o.getString("name"),
                    season = o.getString("season"),
                    looks = looks(o.getString("looks")),
                    packed = ids(o.getString("packed")),
                    notes = o.getString("notes"),
                )
            validateCapsule(capsule)
            require(capsule.items.all { it in ids }) { "行李胶囊的衣物关联无效" }
        }
        rows.getValue("care_logs").forEach { o ->
            require(o.getLong("garmentId") in ids) { "洗护记录关联无效" }
            Care.valueOf(o.getString("beforeStatus"))
            Care.valueOf(o.getString("afterStatus"))
            LocalDateTime.parse(o.getString("time"))
        }
        val made = mutableListOf<File>()
        val oldImages =
            owned("garments", map = ::garment).mapNotNull {
                if (it.image.isNotBlank()) privateImage(it.image) else null
            }
        val garmentMap = mutableMapOf<Long, Long>()
        val outfitMap = mutableMapOf<Long, Long>()
        val planMap = mutableMapOf<Long, Long>()
        writableDatabase.beginTransaction()
        try {
            tables.forEach { writableDatabase.delete(it, "owner=?", arrayOf(owner().toString())) }
            fun insert(
                t: String,
                o: org.json.JSONObject,
                changes: Map<String, Any?> = emptyMap(),
            ): Long {
                val allowed =
                    readableDatabase.rawQuery("PRAGMA table_info($t)", null).use { c ->
                        buildSet { while (c.moveToNext()) add(c.getString(1)) }
                    }
                val v = ContentValues()
                o.keys().forEach { k ->
                    if (k in allowed && k != "id" && k != "owner") {
                        val a = o.get(k)
                        when (a) {
                            org.json.JSONObject.NULL -> v.putNull(k)
                            is Number ->
                                if (a is Double || a is Float) v.put(k, a.toDouble())
                                else v.put(k, a.toLong())
                            else -> v.put(k, a.toString())
                        }
                    }
                }
                changes.forEach { (k, vv) ->
                    when (vv) {
                        null -> v.putNull(k)
                        is Long -> v.put(k, vv)
                        else -> v.put(k, vv.toString())
                    }
                }
                return save(t, 0, v)
            }
            garments.forEach { o ->
                var path = ""
                val encoded = o.optString("photo")
                if (encoded.isNotEmpty()) {
                    val image = Base64.decode(encoded, Base64.NO_WRAP)
                    require(image.size <= 5 * 1024 * 1024) { "备份图片超过 5 MB" }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(image, 0, image.size, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "备份中存在损坏图片" }
                    val dir = File(context.filesDir, "photos/${owner()}").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    made.add(file)
                    file.writeBytes(image)
                    path = file.absolutePath
                }
                garmentMap[o.getLong("id")] = insert("garments", o, mapOf("image" to path))
            }
            fun mapped(o: org.json.JSONObject) =
                json(ids(o.getString("items")).map { garmentMap.getValue(it) })
            rows.getValue("outfits").forEach { o ->
                outfitMap[o.getLong("id")] = insert("outfits", o, mapOf("items" to mapped(o)))
            }
            rows.getValue("plans").forEach { o ->
                planMap[o.getLong("id")] =
                    insert(
                        "plans",
                        o,
                        mapOf(
                            "items" to mapped(o),
                            "outfitId" to (outfitMap[o.optLong("outfitId")] ?: 0L),
                        ),
                    )
            }
            rows.getValue("wears").forEach { o ->
                val pid = if (o.isNull("planId")) null else o.getLong("planId")
                insert(
                    "wears",
                    o,
                    mapOf(
                        "items" to mapped(o),
                        "planId" to pid?.let { planMap[it] ?: -kotlin.math.abs(it) },
                    ),
                )
            }
            rows.getValue("care_logs").forEach { o ->
                insert(
                    "care_logs",
                    o,
                    mapOf("garmentId" to garmentMap.getValue(o.getLong("garmentId"))),
                )
            }
            rows.getValue("locations").forEach { o -> insert("locations", o) }
            rows.getValue("capsules").forEach { o ->
                insert(
                    "capsules",
                    o,
                    mapOf(
                        "looks" to
                            looksJson(
                                looks(o.getString("looks")).map { look ->
                                    look.map { garmentMap.getValue(it) }
                                }
                            ),
                        "packed" to json(ids(o.getString("packed")).map { garmentMap.getValue(it) }),
                    ),
                )
            }
            writableDatabase.setTransactionSuccessful()
        } catch (e: Exception) {
            made.forEach { it.delete() }
            throw e
        } finally {
            writableDatabase.endTransaction()
        }
        oldImages.forEach { it.delete() }
    }
}
