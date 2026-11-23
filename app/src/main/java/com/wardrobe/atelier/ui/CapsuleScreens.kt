package com.wardrobe.atelier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.AppState
import com.wardrobe.atelier.R
import com.wardrobe.atelier.WardrobeViewModel
import com.wardrobe.atelier.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun decodeLooks(text: String): List<List<Long>> =
    text
        .split(';')
        .filter { it.isNotBlank() }
        .map { look -> look.split(',').mapNotNull { it.toLongOrNull() } }

@Composable
fun CapsulesScreen(data: Wardrobe, onBack: () -> Unit, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailTop("轻装出门", onBack, "LESS TO PACK. MORE TO WEAR.")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                EditorialBanner(
                    "几件衣物\n多种可能",
                    "出门前，再整理这一小份。",
                    "A SMALL TRAVEL CAPSULE",
                    R.drawable.capsule_stage,
                )
            }
            item {
                ActionButton(
                    "组合一份行李胶囊",
                    { navigate("capsulebuilder") },
                    Modifier.fillMaxWidth(),
                    icon = "plus",
                )
            }
            item {
                Text(localized("尽量复用已有衣物，留住几套旅行穿搭。行李勾选会保存，方便下次继续收拾。"),
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = Muted,
                )
            }
            if (data.capsules.isEmpty()) item { EmptyPanel("下一趟，轻一点", "先选想要的套数和携带件数，看看衣橱能给出哪些组合。") }
            items(data.capsules, key = { it.id }) { c ->
                Column(
                    Modifier.fillMaxWidth().clip(Panel).background(Color.White).clickable {
                        navigate("capsule/${c.id}")
                    }
                ) {
                    OutfitBoard(
                        data.pieces(c.looks.first()),
                        Modifier.fillMaxWidth().height(160.dp),
                    )
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text(localized("${c.items.size} 件 · ${c.looks.size} 套 · 已装 ${c.packed.size}/${c.items.size}"),
                                fontSize = 12.sp,
                                color = DeepTeal,
                            )
                        }
                        Glyph("arrow")
                    }
                }
            }
        }
    }
}

@Composable
fun CapsuleBuilder(state: AppState, vm: WardrobeViewModel, onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("周末轻装") }
    var season by rememberSaveable { mutableStateOf("不限") }
    var count by rememberSaveable { mutableIntStateOf(3) }
    var limit by rememberSaveable { mutableIntStateOf(6) }
    var outer by rememberSaveable { mutableStateOf(false) }
    var lookText by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var discard by remember { mutableStateOf(false) }
    val back = {
        if (lookText.isNotBlank() || title != "周末轻装" || notes.isNotBlank()) discard = true
        else onBack()
    }
    BackHandler { back() }
    val looks = decodeLooks(lookText)
    val distinct = looks.flatten().distinct()
    val busy = state.busy || generating
    fun invalidate() {
        lookText = ""
        message = ""
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTop("组合一份行李胶囊", back, "BUILD A SMALL CAPSULE")
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(localized("少带几件，也能换着穿。"), fontSize = 24.sp, fontWeight = FontWeight.Black) }
            item {
                AtelierTextField(
                    title,
                    { title = it.take(40) },
                    Modifier.fillMaxWidth(),
                    label = { Text(localized("这趟叫什么？")) },
                    singleLine = true,
                )
            }
            item {
                Text(localized("想试几套组合？"), fontWeight = FontWeight.Bold)
                ChipRow(listOf("3 套", "5 套", "7 套"), "$count 套") {
                    if (!busy) {
                        count = it.substringBefore(' ').toInt()
                        invalidate()
                    }
                }
            }
            item {
                Text(localized("最多携带多少件？"), fontWeight = FontWeight.Bold)
                ChipRow(listOf("6 件", "8 件", "10 件"), "$limit 件") {
                    if (!busy) {
                        limit = it.substringBefore(' ').toInt()
                        invalidate()
                    }
                }
            }
            item {
                Text(localized("季节"), fontWeight = FontWeight.Bold)
                ChipRow(listOf("不限", "春秋", "夏季", "冬季"), season) {
                    if (!busy) {
                        season = it
                        invalidate()
                    }
                }
            }
            item {
                CheckOption(
                    "每套都考虑外套",
                    outer,
                    {
                        outer = it
                        invalidate()
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }
            item {
                ActionButton(
                    if (generating) "正在组合…" else if (looks.isEmpty()) "看看怎么带" else "再试一组",
                    {
                        generating = true
                        scope.launch {
                            try {
                                val result =
                                    withContext(Dispatchers.Default) {
                                        CapsuleEngine.generate(
                                            state.data.garments,
                                            count,
                                            limit,
                                            season,
                                            outer,
                                        )
                                    }
                                lookText =
                                    result.looks.joinToString(";") { row ->
                                        row.joinToString(",") { it.id.toString() }
                                    }
                                message = result.message
                                snapshotFlow { listState.layoutInfo.totalItemsCount }
                                    .first { it > 9 }
                                listState.animateScrollToItem(7)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.message ?: "暂时无法组合"
                                lookText = ""
                            } finally {
                                generating = false
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !busy,
                    icon = "refresh",
                )
            }
            if (message.isNotBlank())
                item {
                    Text(localized(message),
                        Modifier.fillMaxWidth()
                            .background(Lime.copy(alpha = .4f), SmallPanel)
                            .padding(14.dp),
                        lineHeight = 21.sp,
                    )
                }
            if (looks.isNotEmpty()) {
                item {
                    CollectionMetrics(
                        listOf(
                            distinct.size to "携带\n衣物",
                            looks.size to "穿搭\n组合",
                            state.data.pieces(distinct).map { it.location }.distinct().size to
                                "收纳\n位置",
                        )
                    )
                }
                looks.forEachIndexed { index, ids ->
                    item {
                        SectionTitle(
                            "组合 ${index + 1}",
                            "LOOK ${(index + 1).toString().padStart(2, '0')}",
                        )
                        OutfitBoard(
                            state.data.pieces(ids),
                            Modifier.fillMaxWidth().height(220.dp).clip(Panel),
                        )
                    }
                }
                item {
                    AtelierTextField(
                        notes,
                        { notes = it.take(500) },
                        Modifier.fillMaxWidth(),
                        label = { Text(localized("另外记一下（如内衣、洗漱用品）")) },
                    )
                }
            }
            item {
                Text(localized("只组合已登记衣物，按类别、季节及颜色规则生成。鞋可在多套之间复用；套数以不同上下装或连衣裙计算。请自行检查天气、干净程度和贴身衣物等必需品。"),
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        if (looks.isNotEmpty())
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                ActionButton(
                    "保存这份行李胶囊",
                    {
                        vm.perform("行李胶囊已保存", onBack) {
                            saveCapsule(
                                Capsule(name = title, season = season, looks = looks, notes = notes)
                            )
                        }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !busy && title.isNotBlank(),
                    icon = "check",
                )
            }
    }
    if (discard) ConfirmDialog("离开这份草稿？", "这份行李组合和备注还没有保存。", { discard = false }, onBack)
}

@Composable
fun CapsuleDetail(
    c: Capsule,
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("拿衣清单") }
    var deleting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val pieces = state.data.pieces(c.items)
    Column(Modifier.fillMaxSize()) {
        DetailTop(c.name, onBack, "YOUR TRAVEL CAPSULE", translateTitle = false)
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CollectionMetrics(
                    listOf(
                        c.items.size to "携带\n衣物",
                        c.looks.size to "穿搭\n组合",
                        c.packed.size to "已经\n装好",
                    )
                )
            }
            item { ChipRow(listOf("拿衣清单", "穿搭组合"), page) { page = it } }
            if (page == "拿衣清单") {
                item {
                    Text(localized("位置是上次登记的记录。勾选会保存在这份行李胶囊里，不计入穿着历史。"),
                        fontSize = 12.sp,
                        color = Muted,
                        lineHeight = 20.sp,
                    )
                }
                pieces
                    .groupBy { it.location }
                    .forEach { (place, clothes) ->
                        item { SectionTitle(place, "${clothes.size} ITEMS") }
                        items(clothes, key = { it.id }) { g ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(Color.White, SmallPanel)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    g.id in c.packed,
                                    { selected ->
                                        vm.perform(null) { setCapsulePacked(c.id, g.id, selected) }
                                    },
                                    enabled = !state.busy,
                                )
                                GarmentArt(
                                    g,
                                    Modifier.size(60.dp).clip(SmallPanel).clickable {
                                        navigate("detail/${g.id}")
                                    },
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (g.archived)
                                        Text(localized("已归档，出发前确认一下"), fontSize = 12.sp, color = Pink)
                                    else Text(localized(g.category), color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                if (c.packed.size == c.items.size)
                    item {
                        Text(localized("这一小份，已经装好。"),
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black,
                            color = DeepTeal,
                        )
                    }
                item {
                    TextButton(
                        onClick = { vm.perform("已重新打开清单") { resetCapsulePacked(c.id) } },
                        enabled = !state.busy,
                    ) {
                        Text(localized("清空勾选，再用一次"))
                    }
                }
            } else {
                c.looks.forEachIndexed { index, ids ->
                    item {
                        SectionTitle(
                            "组合 ${index + 1}",
                            "LOOK ${(index + 1).toString().padStart(2, '0')}",
                        )
                        OutfitBoard(
                            state.data.pieces(ids),
                            Modifier.fillMaxWidth().height(240.dp).clip(Panel),
                        )
                    }
                    item {
                        Text(
                            state.data.pieces(ids).joinToString(" · ") { it.name },
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = Muted,
                        )
                    }
                }
            }
            if (c.notes.isNotBlank())
                item {
                    SectionTitle("另外记一下", "ONE MORE THING")
                    Text(c.notes, lineHeight = 22.sp)
                }
            item {
                Row {
                    TextButton(onClick = { editing = true }) { Text(localized("名称与备注")) }
                    TextButton(onClick = { deleting = true }) { Text(localized("删除这份胶囊"), color = Muted) }
                }
            }
        }
    }
    if (deleting)
        ConfirmDialog("删除这份行李胶囊？", "只删除这份组合与行李勾选，衣物仍留在衣橱里。", { deleting = false }) {
            vm.perform("已删除行李胶囊", onBack) { deleteCapsule(c.id) }
        }
    if (editing)
        CapsuleInfoDialog(c, { editing = false }) { name, notes ->
            vm.perform("已更新", { editing = false }) {
                saveCapsule(c.copy(name = name, notes = notes))
            }
        }
}

@Composable
private fun CapsuleInfoDialog(c: Capsule, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(c.name) }
    var notes by rememberSaveable { mutableStateOf(c.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Panel,
        title = { Text(localized("名称与备注")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AtelierTextField(
                    name,
                    { name = it.take(40) },
                    label = { Text(localized("名称")) },
                    singleLine = true,
                )
                AtelierTextField(notes, { notes = it.take(500) }, label = { Text(localized("另外记一下")) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, notes) }, enabled = name.isNotBlank()) {
                Text(localized("保存修改"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消")) } },
    )
}
