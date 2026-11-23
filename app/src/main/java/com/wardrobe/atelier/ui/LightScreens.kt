package com.wardrobe.atelier.ui

import com.wardrobe.atelier.presentation.*

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.R
import com.wardrobe.atelier.domain.*

private fun parseIds(value: String) = value.split(',').mapNotNull { it.toLongOrNull() }.toSet()

@Composable
fun LightWelcome(vm: WardrobeViewModel, busy: Boolean, incomingImage: Boolean = false) {
    var accounts by rememberSaveable { mutableStateOf(false) }
    if (accounts) {
        BackHandler { accounts = false }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { accounts = false }, modifier = Modifier.statusBarsPadding()) {
                Text(localized("← 返回轻量入口"))
            }
            Box(Modifier.weight(1f)) { AuthScreen(vm, busy) }
        }
        return
    }
    Column(
        Modifier.fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LanguageSelector()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("hanger", Modifier.size(30.dp), DeepTeal)
            Text(localized("衣序"),
                Modifier.padding(start = 12.dp),
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.weight(1f))
            Micro("LESS INPUT. MORE IDEAS.")
        }
        if (incomingImage)
            Text(localized("已收到图片，先选择一个衣橱，再继续录入。"),
                Modifier.fillMaxWidth()
                    .background(Lime.copy(alpha = .5f), SmallPanel)
                    .padding(12.dp),
                fontSize = 13.sp,
            )
        EditorialBanner(
            "记得衣服\\n不必记流水账".replace("\\n", "\n"),
            "买新衣时录入，用得上时再打开。",
            "YOUR EVERYDAY WARDROBE",
            R.drawable.collection_stage,
            large = true,
        )
        Text(localized("少一点管理，多一点好穿。"), fontSize = 23.sp, fontWeight = FontWeight.Black)
        Text(localized("知道衣服放哪儿，试试没想到的组合。\n不用每天登记穿着，也不用记录洗衣服。"), color = Muted, lineHeight = 24.sp)
        ActionButton(
            "直接开始我的衣橱",
            { vm.openLocalWardrobe(success = null, after = {}) },
            Modifier.fillMaxWidth(),
            enabled = !busy,
            icon = "arrow",
        )
        ActionButton(
            "先逛逛示例衣橱",
            { vm.demo(success = null, after = {}) },
            Modifier.fillMaxWidth(),
            secondary = true,
            enabled = !busy,
        )
        TextButton(onClick = { accounts = true }, enabled = !busy) { Text(localized("已有独立账号？登录或注册")) }
        Text(localized("本机空间无需密码，打开这台设备即可进入。数据只保存在本机，可在“我的”导出加密备份。"),
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
fun FindWardrobeScreen(data: Wardrobe, vm: WardrobeViewModel, navigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var location by rememberSaveable { mutableStateOf("全部位置") }
    var byPlace by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedText by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(selecting) {
        selecting = false
        selectedText = ""
    }
    val selected = parseIds(selectedText).intersect(data.garments.map { it.id }.toSet())
    val matching =
        data.garments.filter {
            it.archived == archived &&
                matchesLocalized(it, query) &&
                (category == "全部" || it.category == category) &&
                (location == "全部位置" || it.location == location)
        }
    val toggle: (Long) -> Unit = { id ->
        selectedText = (if (id in selected) selected - id else selected + id).joinToString(",")
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModuleHeading("想找哪一件？", "01 / FIND YOUR CLOTHES", "录入衣物") { navigate("garment/0") }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localized("${data.active.size} 件衣物，记在这里就好。"),
                        Modifier.weight(1f),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    TextButton(onClick = { navigate("batch") }) { Text(localized("批量录入"), fontSize = 12.sp) }
                }
            }
            item {
                AtelierTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text(localized("蓝色外套 / 宿舍 / 围巾"), fontSize = 14.sp) },
                    leadingIcon = { Glyph("search", Modifier.size(21.dp)) },
                    trailingIcon =
                        if (query.isNotBlank()) {
                            { IconTap("close", "清空搜索", { query = "" }) }
                        } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                focus.clearFocus()
                                keyboard?.hide()
                            }
                        ),
                    singleLine = true,
                )
            }
            item { ChipRow(listOf("全部") + Categories, category) { category = it } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { byPlace = !byPlace }) {
                        Text(localized(if (byPlace) "切回衣物视图" else "按位置找"))
                    }
                    TextButton(
                        onClick = {
                            selecting = !selecting
                            byPlace = false
                            selectedText = ""
                        }
                    ) {
                        Text(localized(if (selecting) "取消整理" else "整理位置"))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { archived = !archived }) {
                        Text(localized(if (archived) "返回在用" else "归档"), color = Muted)
                    }
                }
                if (location != "全部位置")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(location, true)
                        TextButton(onClick = { location = "全部位置" }) { Text(localized("清除位置")) }
                    }
            }
            if (byPlace) {
                item {
                    Text(localized("这里是上次登记的位置；换柜或换季收纳时，再批量更新。"),
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
                items(matching.groupBy { it.location }.toList(), key = { it.first }) {
                    (place, garments) ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(Panel)
                            .background(Color.White)
                            .clickable {
                                location = place
                                byPlace = false
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph("archive", Modifier.size(22.dp), DeepTeal)
                            Text(localized(place),
                                Modifier.weight(1f).padding(start = 10.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            CodeLabel("${garments.size} ITEMS")
                            Glyph("arrow", Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            garments.take(4).forEach {
                                GarmentArt(it, Modifier.size(58.dp).clip(SmallPanel))
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CodeLabel(
                            "${matching.size} " + if (archived) "ARCHIVED ITEMS" else "FOUND ITEMS"
                        )
                        Spacer(Modifier.weight(1f))
                        if (query.isNotBlank() || category != "全部" || location != "全部位置")
                            TextButton(
                                onClick = {
                                    query = ""
                                    category = "全部"
                                    location = "全部位置"
                                }
                            ) {
                                Text(localized("重置筛选"))
                            }
                    }
                }
                items(matching.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { g ->
                            LocationGarmentCard(
                                g,
                                Modifier.weight(1f),
                                selecting,
                                g.id in selected,
                            ) {
                                if (selecting) toggle(g.id) else navigate("detail/${g.id}")
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (matching.isEmpty())
                item {
                    EmptyPanel(
                        "暂时没有找到",
                        if (data.active.isEmpty()) "不用一次录完整个衣柜，从最近买的一件开始。" else "换个关键词，或清除筛选再试试。",
                        "录入衣物",
                    ) {
                        navigate("garment/0")
                    }
                }
        }
        if (selecting)
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(localized("已选 ${selected.size} 件"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                val visibleIds = matching.map { it.id }.toSet()
                val allVisibleSelected = visibleIds.isNotEmpty() && selected.containsAll(visibleIds)
                TextButton(
                    onClick = {
                        selectedText =
                            (if (allVisibleSelected) selected - visibleIds
                                else selected + visibleIds)
                                .joinToString(",")
                    },
                    enabled = visibleIds.isNotEmpty(),
                ) {
                    Text(localized(if (allVisibleSelected) "取消当前全选" else "全选当前结果"))
                }
                ActionButton("移至…", { moving = true }, enabled = selected.isNotEmpty())
            }
    }
    if (moving)
        MoveDialog(data.locations, { moving = false }) { place ->
            vm.moveGarments(selected.toList(), place, success = "位置已更新", after = {
                    moving = false
                    selecting = false
                    selectedText = ""
                })
        }
}

@Composable
fun LocationGarmentCard(
    g: Garment,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(Panel)
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) DeepTeal else Line, Panel)
            .clickable(onClick = onClick)
    ) {
        Box {
            GarmentArt(g, Modifier.fillMaxWidth().aspectRatio(1.14f))
            if (selecting) Checkbox(selected, null, Modifier.align(Alignment.TopEnd).padding(4.dp))
            else Micro(g.category, Ink, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                g.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("archive", Modifier.size(14.dp), DeepTeal)
                Text(
                    g.location,
                    Modifier.padding(start = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DeepTeal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MoveDialog(locations: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var place by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Panel,
        title = { Text(localized("现在放在哪里？")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localized("只更新位置，不增加穿着或洗护记录。"), color = Muted, fontSize = 12.sp)
                ChipRow(locations, place) { place = it }
                AtelierTextField(
                    place,
                    { place = it.take(40) },
                    label = { Text(localized("位置，例如 宿舍衣柜 · 上层")) },
                    singleLine = true,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消")) } },
        confirmButton = {
            TextButton(onClick = { onMove(place) }, enabled = place.isNotBlank()) { Text(localized("更新位置")) }
        },
    )
}

@Composable
fun QuickGarmentEditor(
    original: Garment,
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
) {
    val first =
        if (original.id == 0L) original.copy(color = "其他", season = "未标注", location = "待放置")
        else original
    var name by rememberSaveable(original.id) { mutableStateOf(first.name) }
    var category by rememberSaveable(original.id) { mutableStateOf(first.category) }
    var location by rememberSaveable(original.id) { mutableStateOf(first.location) }
    var color by rememberSaveable(original.id) { mutableStateOf(first.color) }
    var season by rememberSaveable(original.id) { mutableStateOf(first.season) }
    var style by rememberSaveable(original.id) { mutableStateOf(first.style) }
    var image by rememberSaveable(original.id) { mutableStateOf(first.image) }
    var brand by rememberSaveable(original.id) { mutableStateOf(first.brand) }
    var price by
        rememberSaveable(original.id) {
            mutableStateOf(if (first.price > 0) first.price.toString() else "")
        }
    var notes by rememberSaveable(original.id) { mutableStateOf(first.notes) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val current =
        first.copy(
            name = name,
            category = category,
            location = location,
            color = color,
            season = season,
            style = style,
            image = image,
            brand = brand,
            price = price.toDoubleOrNull() ?: 0.0,
            notes = notes,
        )
    val dirty = current != first || price.isNotBlank() && price.toDoubleOrNull() == null
    val back = { if (dirty && !state.busy) discard = true else if (!state.busy) onBack() }
    BackHandler { back() }
    val focus = LocalFocusManager.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {

                vm.importImage(uri, success = null, after = { imported -> image = imported })
            }
        }
    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTop(if (original.id == 0L) "记下一件新衣" else "修改衣物", back, "A LITTLE INPUT IS ENOUGH")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(112.dp).clip(Panel).clickable(enabled = !state.busy) {
                        picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                    }
                ) {
                    GarmentArt(current, Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.BottomEnd)
                            .background(Ink, SmallPanel)
                            .padding(7.dp)
                    ) {
                        Glyph("plus", Modifier.size(18.dp), Color.White)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(localized("照片也可以以后补"), fontWeight = FontWeight.Bold)
                    Text(localized("商品截图或相册照片都可以。\n也能从相册“分享”到衣序。"),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = Muted,
                    )
                    TextButton(
                        onClick = {
                            picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
                        },
                        enabled = !state.busy,
                    ) {
                        Text(localized(if (image.isBlank()) "选择照片" else "更换照片"))
                    }
                }
            }
            if (image.isNotBlank())
                TextButton(onClick = { image = "" }) { Text(localized("移除照片"), color = Muted) }
            Text(localized("是什么衣物？"), fontWeight = FontWeight.Bold)
            ChipRow(Categories, category) {
                category = it
                style = ""
            }
            Text(localized("放在哪里？"), fontWeight = FontWeight.Bold)
            ChipRow(
                (listOf("待放置") +
                        state.data.locations +
                        state.data.active.take(3).map { it.location })
                    .distinct(),
                location,
            ) {
                location = it
            }
            AtelierTextField(
                location,
                { location = it.take(40) },
                Modifier.fillMaxWidth(),
                label = { Text(localized("位置，可细到柜子和层板")) },
                singleLine = true,
            )
            AtelierTextField(
                name,
                { name = it.take(40) },
                Modifier.fillMaxWidth(),
                label = { Text(localized("名字（可不填，自动命名）")) },
                singleLine = true,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(localized(if (expanded) "收起可选信息 ↑" else "颜色、季节和更多信息（可后补） ↓"))
            }
            RevealPanel(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(localized("颜色"), fontWeight = FontWeight.Bold)
                    ChipRow(PaletteNames, color) { color = it }
                    Text(localized("季节"), fontWeight = FontWeight.Bold)
                    ChipRow(Seasons, season) { season = it }
                    Text(localized("款式"), fontWeight = FontWeight.Bold)
                    ChipRow(
                        listOf("未标注") + Silhouettes.getValue(category),
                        style.ifBlank { "未标注" },
                    ) {
                        style = if (it == "未标注") "" else it
                    }
                    AtelierTextField(
                        brand,
                        { brand = it.take(40) },
                        Modifier.fillMaxWidth(),
                        label = { Text(localized("品牌（选填）")) },
                        singleLine = true,
                    )
                    AtelierTextField(
                        price,
                        { price = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(localized("价格（选填）")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    AtelierTextField(
                        notes,
                        { notes = it.take(500) },
                        Modifier.fillMaxWidth(),
                        label = { Text(localized("备注（选填）")) },
                    )
                }
            }
            val similar = WardrobeSearch.similar(state.data.garments, category, color, original.id)
            if (original.id == 0L && similar.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().background(Color.White, Panel).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(localized("衣橱里已有 ${similar.size} 件${if (color == "其他") "同类别" else "同色同类别"}衣物"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Text(
                        similar.take(3).joinToString(" · ") { "${it.name}（${it.location}）" },
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    Text(localized("仅按你填写的类别和颜色对照，方便买前看一眼。"), color = Muted, fontSize = 11.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            ActionButton(
                "保存衣物",
                {
                    focus.clearFocus()
                    if (
                        price.isNotBlank() &&
                            (price.toDoubleOrNull() == null || !price.toDouble().isFinite())
                    )
                        vm.error("请输入有效的价格")
                    else vm.saveQuickGarment(current, success = "衣物已记下", after = onBack)
                },
                Modifier.fillMaxWidth(),
                enabled = !state.busy,
                icon = "check",
            )
        }
    }
    if (discard) ConfirmDialog("离开这次录入？", "这次填写的内容还没有保存。", { discard = false }, onBack)
}

@Composable
fun LightGarmentDetail(
    g: Garment,
    data: Wardrobe,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var move by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DetailTop("衣物档案", onBack, "FIND IT. STYLE IT.")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GarmentArt(g, Modifier.fillMaxWidth().height(235.dp).clip(Panel)) }
            item {
                Text(g.name, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(localized("${g.category} · ${g.color} · ${g.season}"), color = Muted, fontSize = 13.sp)
            }
            item {
                Column(
                    Modifier.fillMaxWidth().background(Ink, Panel).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Micro("LAST SAVED LOCATION", Teal)
                    Text(
                        g.location,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { move = true }) { Text(localized("换位置了？更新一下 →"), color = Teal) }
                }
            }
            item {
                ActionButton(
                    "今天就想穿这件",
                    { navigate("inspire/${g.id}") },
                    Modifier.fillMaxWidth(),
                    enabled = !g.archived,
                    icon = "outfit",
                )
            }
            item {
                ActionButton(
                    "修改资料",
                    { navigate("garment/${g.id}") },
                    Modifier.fillMaxWidth(),
                    secondary = true,
                    icon = "edit",
                )
            }
            if (g.notes.isNotBlank()) item { Text(g.notes, color = Muted) }
            item {
                Row {
                    TextButton(onClick = { archive = true }) {
                        Text(localized(if (g.archived) "恢复衣物" else "归档衣物"))
                    }
                    TextButton(onClick = { navigate("legacydetail/${g.id}") }) {
                        Text(localized("更多档案与记录"), color = Muted)
                    }
                }
            }
        }
    }
    if (move)
        MoveDialog(data.locations, { move = false }) {
            vm.moveGarments(listOf(g.id), it, success = "位置已更新", after = { move = false })
        }
    if (archive)
        ConfirmDialog(
            if (g.archived) "恢复这件衣物？" else "归档这件衣物？",
            "归档后不再参与新搭配，原有记录仍保留。",
            { archive = false },
        ) {
            vm.archive(g.id, !g.archived, success = "已更新", after = onBack)
        }
}

@Composable
fun LightProfile(state: AppState, vm: WardrobeViewModel, navigate: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ModuleHeading("用到时，再来就好", "03 / YOUR OWN RHYTHM") }
        item { LanguageSelector() }
        item { Text(localized("这里没有每日任务。你的衣物和灵感，会留在这里。"), color = Muted, lineHeight = 21.sp) }
        item {
            CollectionMetrics(
                listOf(
                    state.data.active.size to "已录入\n衣物",
                    state.data.outfits.size to "收藏\n搭配",
                    state.data.active.map { it.location }.distinct().size to "收纳\n位置",
                )
            )
        }
        item { ProfileRow("outfit", "收藏的搭配", "不用重新想，随时拿出一套") { navigate("saved") } }
        item { ProfileRow("search", "买前看一眼", "同类同色的衣物，衣橱里是否已经有了") { navigate("compare") } }
        item { ProfileRow("archive", "收纳位置", "换季搬动时，再整理就好") { navigate("locations") } }
        item { ProfileRow("suitcase", "轻装出门", "少带几件，组合几套旅行穿搭") { navigate("capsules") } }
        item { BackupPanel(vm, state) }
        item { SectionTitle("可选工具", "ONLY IF YOU NEED THEM") }
        item {
            Text(localized("喜欢记录时再用。不使用这些工具，也能完整找衣和获得搭配灵感。"),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }
        item { ProfileRow("calendar", "穿搭日历", "提前安排，确认穿过时才写入历史") { navigate("calendar") } }
        item { ProfileRow("care", "洗护记录", "仅手动维护，不影响默认推荐") { navigate("care") } }
        item { ProfileRow("stats", "记录与统计", "只分析主动登记的数据") { navigate("statistics") } }
        item { ProfileRow("user", "账号与设置", "切换空间、修改昵称、查看历史") { navigate("profile") } }
    }
}

@Composable
fun PurchaseCompare(data: Wardrobe, onBack: () -> Unit, navigate: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("上装") }
    var color by rememberSaveable { mutableStateOf("其他") }
    val matches = WardrobeSearch.similar(data.garments, category, color)
    Column(Modifier.fillMaxSize()) {
        DetailTop("买前看一眼", onBack, "SHOP YOUR OWN WARDROBE")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(localized("购物前，先看看自己已有的。"), fontSize = 22.sp, fontWeight = FontWeight.Black) }
            item { ChipRow(Categories, category) { category = it } }
            item {
                ChipRow(
                    listOf("不限颜色") + PaletteNames.filter { it != "其他" },
                    if (color == "其他") "不限颜色" else color,
                ) {
                    color = if (it == "不限颜色") "其他" else it
                }
            }
            item { SectionTitle("已拥有 ${matches.size} 件", "ALREADY YOURS") }
            item { Text(localized("按登记的类别与颜色对照，不识别照片，也不替你决定是否购买。"), color = Muted, fontSize = 12.sp) }
            if (matches.isEmpty()) item { EmptyPanel("没有同类记录", "可能还没录入，也可能这次确实需要一件新的。") }
            items(matches.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { g ->
                        LocationGarmentCard(g, Modifier.weight(1f)) { navigate("detail/${g.id}") }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
