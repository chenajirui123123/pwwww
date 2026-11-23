package com.wardrobe.atelier.presentation

/** Named user actions shared by the welcome, authentication and account settings screens. */
interface AccountActions {
    fun register(username: String, password: String, nickname: String, confirmation: String)
    fun login(username: String, password: String)
    fun logout()
    fun updateNickname(nickname: String, after: () -> Unit = {})
    fun changePassword(oldPassword: String, newPassword: String, confirmation: String, after: () -> Unit = {})
    fun openLocalWardrobe()
    fun openDemoWardrobe(success: String? = null)
}
