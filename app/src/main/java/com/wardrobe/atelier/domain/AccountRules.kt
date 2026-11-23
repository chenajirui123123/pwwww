package com.wardrobe.atelier.domain

/** Account rules are independent of Android widgets, controllers and the database. */
object AccountRules {
    fun validateRegistration(username: String, password: String, nickname: String, confirmation: String) {
        validateNewAccount(username, password, nickname)
        require(password == confirmation) { "两次输入的密码不一致" }
    }

    fun validateNewAccount(username: String, password: String, nickname: String) {
        require(username.trim().matches(Regex("[A-Za-z0-9_]{3,24}"))) { "账号需为 3–24 位字母、数字或下划线" }
        require(username.trim().lowercase() !in setOf("wardrobe_demo", "wardrobe_local")) {
            "此账号名称为内置衣橱保留，请选择其他名称"
        }
        require(password.length in 8..64) { "密码需为 8–64 位字符" }
        validateNickname(nickname)
    }

    fun validateLogin(username: String, password: String) {
        require(username.isNotBlank() && password.isNotEmpty()) { "请输入账号和密码" }
        require(username.trim().length <= 24 && password.length <= 64) { "账号或密码不正确" }
    }

    fun validateNickname(nickname: String) {
        require(nickname.trim().length in 1..20) { "昵称需为 1–20 个字" }
    }

    fun validatePasswordChange(oldPassword: String, newPassword: String, confirmation: String) {
        validateNewPassword(oldPassword, newPassword)
        require(newPassword == confirmation) { "两次输入的密码不一致" }
    }

    fun validateNewPassword(oldPassword: String, newPassword: String) {
        require(oldPassword.isNotEmpty()) { "请输入原密码" }
        require(newPassword.length in 8..64) { "新密码需为 8–64 位字符" }
    }
}
