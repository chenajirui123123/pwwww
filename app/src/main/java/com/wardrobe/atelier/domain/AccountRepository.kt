package com.wardrobe.atelier.domain

/** Blocking model operations. Controllers dispatch them to a worker thread. */
interface AccountRepository {
    fun account(): Account?
    fun register(username: String, pass: String, nickname: String): Account
    fun login(username: String, pass: String): Account
    fun logout()
    fun rename(nickname: String)
    fun changePassword(old: String, new: String)
    fun openLocalWardrobe(): Account
    fun demo(): Account
}
