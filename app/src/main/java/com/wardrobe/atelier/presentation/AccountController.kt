package com.wardrobe.atelier.presentation

import com.wardrobe.atelier.domain.AccountRepository
import com.wardrobe.atelier.domain.AccountRules

/** Controller: receives named user actions, coordinates model work and publishes display results. */
internal class AccountController(
    private val repository: AccountRepository,
    private val operations: OperationCoordinator,
    private val onLogout: () -> Unit = {},
) : AccountActions {
    override fun register(username: String, password: String, nickname: String, confirmation: String) {
        operations.execute("欢迎来到衣序") {
            AccountRules.validateRegistration(username, password, nickname, confirmation)
            repository.register(username, password, nickname)
        }
    }

    override fun login(username: String, password: String) {
        operations.execute("欢迎来到衣序") {
            AccountRules.validateLogin(username, password)
            repository.login(username, password)
        }
    }

    override fun logout() {
        operations.execute("已退出登录", after = { onLogout() }) { repository.logout() }
    }

    override fun updateNickname(nickname: String, after: () -> Unit) {
        operations.execute("昵称已更新", { after() }) {
            AccountRules.validateNickname(nickname)
            repository.rename(nickname)
        }
    }

    override fun changePassword(oldPassword: String, newPassword: String, confirmation: String, after: () -> Unit) {
        operations.execute("密码已修改", { after() }) {
            AccountRules.validatePasswordChange(oldPassword, newPassword, confirmation)
            repository.changePassword(oldPassword, newPassword)
        }
    }

    override fun openLocalWardrobe() {
        operations.execute { repository.openLocalWardrobe() }
    }

    override fun openDemoWardrobe(success: String?) {
        operations.execute(success) { repository.demo() }
    }
}
