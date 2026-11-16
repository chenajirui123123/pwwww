package com.wardrobe.atelier.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.*
import com.wardrobe.atelier.domain.*

@Composable
fun ProfileScreen(
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    val account = state.account ?: return
    var rename by remember { mutableStateOf(false) }
    var nick by rememberSaveable { mutableStateOf(account.nickname) }
    var logout by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        DetailTop("个人中心", onBack, "PERSONAL / SPACE")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Ink, Panel)
                        .registrationMarks(Teal.copy(alpha = .35f))
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(66.dp).background(Teal, Panel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            account.nickname.take(1),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Ink,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.nickname,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Micro(
                            when (account.username) {
                                "wardrobe_local" -> "本机空间 · 无需密码"
                                "wardrobe_demo" -> "示例衣橱"
                                else -> "@${account.username}"
                            },
                            Color(0xFFBDC8C5),
                            Modifier.padding(top = 5.dp),
                        )
                    }
                    IconTap("edit", "修改昵称", { rename = true }, Teal)
                }
            }
            item { ProfileRow("clock", "穿着历史", "查看、修正与撤销实际记录") { navigate("history") } }
            item { ProfileRow("care", "洗护历史", "每一次状态变化都有记录") { navigate("carehistory") } }
            item { ProfileRow("archive", "收纳位置", "管理衣柜、鞋架与收纳空间") { navigate("locations") } }
            if (account.username !in setOf("wardrobe_demo", "wardrobe_local"))
                item { ProfileRow("user", "修改密码", "保护本机账号") { passwordDialog = true } }
            item { BackupPanel(vm, state) }
            item {
                Column(
                    Modifier.background(Color.White, Panel).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Micro("ABOUT / 衣序轻量版 2.0")
                    Text(localized("让衣物有归处，让穿搭有灵感。"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(localized("本机离线保存。无需每天维护洗护或穿着状态；找衣与搭配不依赖这些记录。不同账号的数据彼此独立。"),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Text(localized("计划不会自动计入穿着次数；历史不会在 30 天后自动删除。统计页的 30 / 60 / 90 天仅用于查看无记录的时长，不代表衣物没有穿过。"),
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Text(localized("卸载应用或清除应用数据会移除本机内容，请提前导出备份。"), color = Pink, fontSize = 12.sp)
                }
            }
            item {
                ActionButton("退出当前账号", { logout = true }, Modifier.fillMaxWidth(), secondary = true)
            }
        }
    }
    if (rename)
        AlertDialog(
            onDismissRequest = { rename = false },
            shape = Panel,
            title = { Text(localized("修改昵称")) },
            text = {
                AtelierTextField(
                    nick,
                    { nick = it.take(20) },
                    singleLine = true,
                    label = { Text(localized("昵称")) },
                )
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(localized("取消")) } },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = { vm.perform("昵称已更新", { rename = false }) { rename(nick) } },
                ) {
                    Text(localized("保存"))
                }
            },
        )
    if (passwordDialog)
        AlertDialog(
            onDismissRequest = {
                passwordDialog = false
                oldPass = ""
                newPass = ""
            },
            shape = Panel,
            title = { Text(localized("修改密码")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AtelierTextField(
                        oldPass,
                        { oldPass = it },
                        singleLine = true,
                        label = { Text(localized("原密码")) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    AtelierTextField(
                        newPass,
                        { newPass = it },
                        singleLine = true,
                        label = { Text(localized("新密码，至少 8 位")) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordDialog = false
                        oldPass = ""
                        newPass = ""
                    }
                ) {
                    Text(localized("取消"))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        vm.perform(
                            "密码已修改",
                            {
                                passwordDialog = false
                                oldPass = ""
                                newPass = ""
                            },
                        ) {
                            changePassword(oldPass, newPass)
                        }
                    },
                ) {
                    Text(localized("保存"))
                }
            },
        )
    if (logout)
        ConfirmDialog("退出当前账号？", "本机衣物与历史记录仍然保留，再次进入同一空间即可查看。", { logout = false }) {
            logout = false
            vm.perform("已退出登录") { logout() }
        }
}

@Composable
fun ProfileRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, SmallPanel)
            .border(1.dp, Line, SmallPanel)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(Paper, SmallPanel),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, Modifier.size(23.dp), DeepTeal)
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(localized(title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(localized(subtitle), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Glyph("arrow", Modifier.size(18.dp), Muted)
    }
}

@Composable
fun LocationScreen(data: Wardrobe, vm: WardrobeViewModel, onBack: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        DetailTop("收纳位置", onBack, "STORAGE / LOCATIONS")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AtelierTextField(
                        value,
                        { value = it.take(40) },
                        label = { Text(localized("新位置名称")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = SmallPanel,
                    )
                    ActionButton(
                        "添加",
                        { vm.perform("位置已添加", { value = "" }) { addLocation(value) } },
                    )
                }
            }
            item { Text(localized("重命名位置会同步更新相关衣物，不改变洗护状态。"), fontSize = 12.sp, color = Muted) }
            items(data.locations) { name ->
                Row(
                    Modifier.fillMaxWidth().background(Color.White, SmallPanel).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(
                            "${data.garments.count{it.location==name}} 件衣物（含归档）",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    IconTap(
                        "edit",
                        "重命名$name",
                        {
                            editing = name
                            editValue = name
                        },
                    )
                    IconTap("close", "删除$name", { removing = name })
                }
            }
        }
    }
    if (editing != null)
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = Panel,
            title = { Text(localized("重命名位置")) },
            text = { AtelierTextField(editValue, { editValue = it.take(40) }, singleLine = true) },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(localized("取消")) } },
            confirmButton = {
                TextButton(
                    onClick = {
                        val old = editing!!
                        vm.perform("位置已更新", { editing = null }) { renameLocation(old, editValue) }
                    }
                ) {
                    Text(localized("保存"))
                }
            },
        )
    if (removing != null)
        ConfirmDialog("删除这个位置？", "仍有关联衣物的位置不能删除，请先移动相关衣物。", { removing = null }) {
            val name = removing!!
            removing = null
            vm.perform("位置已删除") { deleteLocation(name) }
        }
}

@Composable
fun HistoryScreen(data: Wardrobe, onBack: () -> Unit, navigate: (String) -> Unit) {
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var showVoided by rememberSaveable { mutableStateOf(true) }
    val records =
        data.wears.filter {
            (from.isBlank() || it.date >= from) &&
                (to.isBlank() || it.date <= to) &&
                (showVoided || !it.voided)
        }
    Column(Modifier.fillMaxSize()) {
        DetailTop("穿着历史", onBack, "HISTORY / RETAINED")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        DateField("起始日期", from.ifBlank { "不限" }, { from = it })
                    }
                    Box(Modifier.weight(1f)) { DateField("结束日期", to.ifBlank { "不限" }, { to = it }) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localized("显示已撤销记录"), fontSize = 13.sp)
                    Switch(showVoided, { showVoided = it })
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            from = ""
                            to = ""
                        }
                    ) {
                        Text(localized("清除日期"))
                    }
                }
            }
            if (from.isNotBlank() && to.isNotBlank() && from > to)
                item { Text(localized("起始日期不能晚于结束日期"), color = Pink) }
            item {
                ActionButton(
                    "记录实际穿着",
                    { navigate("wear/0") },
                    Modifier.fillMaxWidth(),
                    icon = "plus",
                )
            }
            if (records.isEmpty()) item { EmptyPanel("没有符合条件的记录", "历史会一直保留，可试着调整日期范围。") }
            items(records, key = { it.id }) { w -> WearRow(w, data) { navigate("wear/${w.id}") } }
        }
    }
}

@Composable
fun CareHistory(data: Wardrobe, onBack: () -> Unit, garmentId: Long = 0) {
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    val logs =
        data.careLogs.filter {
            (garmentId == 0L || it.garmentId == garmentId) &&
                (from.isBlank() || it.time.take(10) >= from) &&
                (to.isBlank() || it.time.take(10) <= to)
        }
    Column(Modifier.fillMaxSize()) {
        DetailTop("洗护历史", onBack, "CARE / TIMELINE")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        DateField("起始日期", from.ifBlank { "不限" }, { from = it })
                    }
                    Box(Modifier.weight(1f)) { DateField("结束日期", to.ifBlank { "不限" }, { to = it }) }
                }
                TextButton(
                    onClick = {
                        from = ""
                        to = ""
                    }
                ) {
                    Text(localized("清除日期"))
                }
            }
            if (from.isNotBlank() && to.isNotBlank() && from > to)
                item { Text(localized("起始日期不能晚于结束日期"), color = Pink) }
            if (logs.isEmpty()) item { EmptyPanel("暂无洗护变化", "每次实际状态变更都会在这里留下记录。") }
            items(logs, key = { it.id }) { log ->
                val g = data.garments.find { it.id == log.garmentId }
                Row(
                    Modifier.background(Color.White, SmallPanel).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (g != null) GarmentArt(g, Modifier.size(58.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g?.name ?: "历史衣物", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(localized("${log.before.label} → ${log.after.label}"),
                            color = Ink,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Micro(
                            log.time.take(16).replace('T', ' '),
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
