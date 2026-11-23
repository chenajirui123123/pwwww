package com.wardrobe.atelier.data

import android.content.Context
import android.net.Uri
import com.wardrobe.atelier.domain.*

/** SQLite adapter. Existing schema, transactions, backup format and files remain unchanged. */
class LocalWardrobeRepository(context: Context) : WardrobeRepository {
    private val store = LocalStore(context.applicationContext)
    override fun account(): Account? = store.account()
    override fun register(username: String, pass: String, nickname: String): Account = store.register(username, pass, nickname)
    override fun openLocalWardrobe(): Account = store.openLocalWardrobe()
    override fun saveQuickGarment(g: Garment): Long = store.saveQuickGarment(g)
    override fun saveQuickGarments(garments: List<Garment>): List<Long> = store.saveQuickGarments(garments)
    override fun importImages(uris: List<String>): List<String> = store.importImages(uris.map(Uri::parse))
    override fun discardDraftImages(paths: List<String>): Unit = store.discardDraftImages(paths)
    override fun moveGarments(ids: List<Long>, location: String): Unit = store.moveGarments(ids, location)
    override fun login(username: String, pass: String): Account = store.login(username, pass)
    override fun logout(): Unit = store.logout()
    override fun rename(nickname: String): Unit = store.rename(nickname)
    override fun changePassword(old: String, new: String): Unit = store.changePassword(old, new)
    override fun snapshot(): Wardrobe = store.snapshot()
    override fun saveCapsule(c: Capsule): Long = store.saveCapsule(c)
    override fun setCapsulePacked(id: Long, garmentId: Long, checked: Boolean): Unit = store.setCapsulePacked(id, garmentId, checked)
    override fun resetCapsulePacked(id: Long): Unit = store.resetCapsulePacked(id)
    override fun deleteCapsule(id: Long): Unit = store.deleteCapsule(id)
    override fun saveGarment(g: Garment): Long = store.saveGarment(g)
    override fun archive(id: Long, archived: Boolean): Unit = store.archive(id, archived)
    override fun saveOutfit(o: Outfit): Long = store.saveOutfit(o)
    override fun deleteOutfit(id: Long): Unit = store.deleteOutfit(id)
    override fun savePlan(p: Plan): Long = store.savePlan(p)
    override fun deletePlan(id: Long): Unit = store.deletePlan(id)
    override fun saveWear(w: Wear): Long = store.saveWear(w)
    override fun voidWear(id: Long): Unit = store.voidWear(id)
    override fun setCare(id: Long, state: Care): Unit = store.setCare(id, state)
    override fun addLocation(name: String): Unit = store.addLocation(name)
    override fun renameLocation(old: String, new: String): Unit = store.renameLocation(old, new)
    override fun deleteLocation(name: String): Unit = store.deleteLocation(name)
    override fun importImage(uri: String): String = store.importImage(Uri.parse(uri))
    override fun demo(): Account = store.demo()
    override fun exportBackup(uri: String, passphrase: String): Unit = store.exportBackup(Uri.parse(uri), passphrase)
    override fun importBackup(uri: String, passphrase: String): Unit = store.importBackup(Uri.parse(uri), passphrase)
    override fun close() = store.close()
}
