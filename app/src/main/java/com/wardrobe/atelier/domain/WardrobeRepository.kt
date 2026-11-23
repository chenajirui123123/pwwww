package com.wardrobe.atelier.domain

/** Synchronous persistence port. Call on an IO dispatcher; implementations own transactions. */
interface WardrobeRepository : AutoCloseable {
    fun account(): Account?
    fun register(username: String, pass: String, nickname: String): Account
    fun openLocalWardrobe(): Account
    fun saveQuickGarment(g: Garment): Long
    fun saveQuickGarments(garments: List<Garment>): List<Long>
    fun importImages(uris: List<String>): List<String>
    fun discardDraftImages(paths: List<String>): Unit
    fun moveGarments(ids: List<Long>, location: String): Unit
    fun login(username: String, pass: String): Account
    fun logout(): Unit
    fun rename(nickname: String): Unit
    fun changePassword(old: String, new: String): Unit
    fun snapshot(): Wardrobe
    fun saveCapsule(c: Capsule): Long
    fun setCapsulePacked(id: Long, garmentId: Long, checked: Boolean): Unit
    fun resetCapsulePacked(id: Long): Unit
    fun deleteCapsule(id: Long): Unit
    fun saveGarment(g: Garment): Long
    fun archive(id: Long, archived: Boolean): Unit
    fun saveOutfit(o: Outfit): Long
    fun deleteOutfit(id: Long): Unit
    fun savePlan(p: Plan): Long
    fun deletePlan(id: Long): Unit
    fun saveWear(w: Wear): Long
    fun voidWear(id: Long): Unit
    fun setCare(id: Long, state: Care): Unit
    fun addLocation(name: String): Unit
    fun renameLocation(old: String, new: String): Unit
    fun deleteLocation(name: String): Unit
    fun importImage(uri: String): String
    fun demo(): Account
    fun exportBackup(uri: String, passphrase: String): Unit
    fun importBackup(uri: String, passphrase: String): Unit
}
