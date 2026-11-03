package com.wardrobe.atelier.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wardrobe.atelier.*
import java.time.LocalDate

@Composable
fun BackupPanel(vm: WardrobeViewModel, state: AppState) {
    var mode by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val secret = pass
            pass = ""
            mode = ""
            if (uri != null) vm.perform("加密备份已导出") { exportBackup(uri, secret) }
            else vm.notify("已取消导出")
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val secret = pass
            pass = ""
            mode = ""
            if (uri != null) vm.perform("备份已恢复") { importBackup(uri, secret) }
            else vm.notify("已取消恢复")
        }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ProfileRow("archive", "导出加密备份", "衣物、照片、搭配与全部历史") { mode = "export" }
        ProfileRow("clock", "从备份恢复", "需要备份口令，会替换当前账号内容") { mode = "restore" }
    }
    if (mode.isNotEmpty())
        AlertDialog(
            onDismissRequest = {
                mode = ""
                pass = ""
            },
            shape = Panel,
            title = { Text(if (mode == "export") "设置备份口令" else "恢复加密备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (mode == "export") "至少 8 位。请记住口令，恢复时需要输入；账号密码不会写入备份。"
                        else "恢复会替换当前账号的全部衣物、搭配和历史。其他账号不受影响。建议先导出当前数据备份。"
                    )
                    AtelierTextField(
                        pass,
                        { pass = it },
                        singleLine = true,
                        label = { Text("备份口令") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mode = ""
                        pass = ""
                    }
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pass.length >= 8 && !state.busy,
                    onClick = {
                        if (mode == "export") export.launch("衣序_${LocalDate.now()}.yixu")
                        else restore.launch(arrayOf("*/*"))
                    },
                ) {
                    Text(if (mode == "export") "选择保存位置" else "确认替换并选择备份", color = Pink)
                }
            },
        )
}
