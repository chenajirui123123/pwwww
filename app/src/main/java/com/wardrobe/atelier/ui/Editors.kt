package com.wardrobe.atelier.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.*
import com.wardrobe.atelier.domain.*
import java.time.LocalDate

@Composable
fun DetailRouter(
    route: String,
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    val parts = route.split('/')
    val id = parts.getOrNull(1)?.toLongOrNull() ?: 0
    val date = parts.getOrNull(2) ?: LocalDate.now().toString()
    val data = state.data
    when (parts[0]) {
        "garment" ->
            GarmentEditor(data.garments.find { it.id == id } ?: Garment(), state, vm, onBack)
        "detail" ->
            data.garments
                .find { it.id == id }
                ?.let { GarmentDetail(it, data, vm, onBack, navigate) } ?: Missing(onBack)
        "outfit" -> OutfitEditor(data.outfits.find { it.id == id } ?: Outfit(), state, vm, onBack)
        "outfitdetail" ->
            data.outfits.find { it.id == id }?.let { OutfitDetail(it, data, vm, onBack, navigate) }
                ?: Missing(onBack)
        "plan" ->
            PlanEditor(data.plans.find { it.id == id } ?: Plan(date = date), state, vm, onBack)
        "planoutfit" ->
            data.outfits
                .find { it.id == id }
                ?.let {
                    PlanEditor(
                        Plan(title = it.name, items = it.items, outfitId = it.id, date = date),
                        state,
                        vm,
                        onBack,
                    )
                } ?: Missing(onBack)
        "wear" ->
            WearEditor(data.wears.find { it.id == id } ?: Wear(date = date), state, vm, onBack)
        "wearoutfit" ->
            data.outfits
                .find { it.id == id }
                ?.let { WearEditor(Wear(items = it.items), state, vm, onBack) } ?: Missing(onBack)
        "confirm" ->
            data.plans
                .find { it.id == id }
                ?.let { p ->
                    WearEditor(
                        Wear(
                            date =
                                if (LocalDate.parse(p.date).isAfter(LocalDate.now()))
                                    LocalDate.now().toString()
                                else p.date,
                            items = p.items,
                            planId = p.id,
                            notes = p.notes,
                        ),
                        state,
                        vm,
                        onBack,
                    )
                } ?: Missing(onBack)
        "profile" -> ProfileScreen(state, vm, onBack, navigate)
        "locations" -> LocationScreen(data, vm, onBack)
        "history" -> HistoryScreen(data, onBack, navigate)
        "carehistory" -> CareHistory(data, onBack, id)
        else -> Missing(onBack)
    }
}

@Composable
private fun Missing(onBack: () -> Unit) {
    Column {
        DetailTop("记录不存在", onBack)
        EmptyPanel("记录不可用", "请返回后重试")
    }
}

@Composable
fun DetailTop(title: String, onBack: () -> Unit, code: String = "WARDROBE / RECORD") {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap("back", "返回", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Micro(code, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EditorFrame(
    title: String,
    dirty: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var ask by remember { mutableStateOf(false) }
    val back: () -> Unit = {
        if (!busy) {
            if (dirty) ask = true else onBack()
        }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTop(title, back, "EDITOR / LOCAL STORAGE")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        Box(Modifier.fillMaxWidth().background(Paper).padding(16.dp)) {
            ActionButton(
                if (busy) "正在保存…" else saveLabel,
                {
                    focusManager.clearFocus()
                    onSave()
                },
                Modifier.fillMaxWidth(),
                enabled = !busy,
                icon = "check",
            )
        }
    }
    if (ask)
        ConfirmDialog("放弃未保存的修改？", "当前编辑内容尚未保存。", { ask = false }) {
            ask = false
            onBack()
        }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    single: Boolean = true,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value,
        onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = single,
        minLines = if (single) 1 else 3,
        shape = SmallPanel,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
private fun Label(text: String, code: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Micro(code)
    }
}

@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, pastOnly: Boolean = false) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth()
            .clip(SmallPanel)
            .background(Color.White)
            .border(1.dp, Line, SmallPanel)
            .clickable {
                val d = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                DatePickerDialog(
                        context,
                        { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth,
                    )
                    .apply {
                        if (pastOnly) datePicker.maxDate = System.currentTimeMillis()
                        show()
                    }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("calendar", Modifier.size(22.dp), Muted)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Micro(label)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Glyph("arrow", Modifier.size(18.dp))
    }
}

@Composable
private fun GarmentEditor(
    original: Garment,
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(original.id) { mutableStateOf(original.name) }
    var category by rememberSaveable(original.id) { mutableStateOf(original.category) }
    var color by rememberSaveable(original.id) { mutableStateOf(original.color) }
    var season by rememberSaveable(original.id) { mutableStateOf(original.season) }
    var location by rememberSaveable(original.id) { mutableStateOf(original.location) }
    var brand by rememberSaveable(original.id) { mutableStateOf(original.brand) }
    var price by
        rememberSaveable(original.id) {
            mutableStateOf(if (original.price == 0.0) "" else original.price.toString())
        }
    var notes by rememberSaveable(original.id) { mutableStateOf(original.notes) }
    var image by rememberSaveable(original.id) { mutableStateOf(original.image) }
    var style by rememberSaveable(original.id) { mutableStateOf(original.style) }
    val current =
        original.copy(
            name = name,
            category = category,
            style = style,
            color = color,
            season = season,
            location = location,
            brand = brand,
            price = price.toDoubleOrNull() ?: 0.0,
            notes = notes,
            image = image,
        )
    val dirty = current != original || price.isNotEmpty() && price.toDoubleOrNull() == null
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                var path = ""
                vm.perform(null, after = { image = path }) { path = importImage(uri) }
            } else vm.notify("已取消选择，原图片保持不变")
        }
    EditorFrame(
        if (original.id == 0L) "录入衣物" else "编辑衣物",
        dirty,
        state.busy,
        onBack,
        {
            if (price.isNotBlank() && price.toDoubleOrNull() == null) vm.error("请输入有效的价格")
            else vm.perform("衣物已保存", onBack) { saveGarment(current) }
        },
    ) {
        Box(
            Modifier.fillMaxWidth().height(220.dp).clip(Panel).clickable(enabled = !state.busy) {
                picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }
        ) {
            GarmentArt(current, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(Ink, SmallPanel)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    if (image.isBlank()) "＋ 从相册选择图片" else "更换图片",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text("支持 JPG / PNG / WebP，最大 5 MB。未选图片时使用分类插画。", fontSize = 11.sp, color = Muted)
        if (image.isNotEmpty())
            TextButton(onClick = { image = "" }) {
                Text("移除当前照片，使用插画", color = Muted, fontSize = 12.sp)
            }
        Field("衣物名称 *", name, { name = it.take(40) })
        Label("衣物类别")
        ChipRow(Categories, category) {
            category = it
            style = ""
        }
        Label("衣物款式")
        ChipRow(
            Silhouettes.getValue(category),
            style.ifBlank { Silhouettes.getValue(category).first() },
        ) {
            style = it
        }
        Label("颜色")
        ChipRow(PaletteNames, color) { color = it }
        Label("适用季节")
        ChipRow(Seasons, season) { season = it }
        Label("收纳位置")
        ChipRow(state.data.locations, location) { location = it }
        Field("位置名称 *", location, { location = it.take(40) })
        Field("品牌（选填）", brand, { brand = it.take(40) })
        Field("购入价格 / 元（选填）", price, { price = it }, numeric = true)
        Field("备注（选填）", notes, { notes = it.take(500) }, single = false)
        Text("新衣物默认为可穿；编辑资料不会改动已有洗护状态。", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GarmentPicker(
    data: Wardrobe,
    selected: List<Long>,
    onSelect: (List<Long>) -> Unit,
    includeArchived: Boolean = false,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    val source =
        data.garments.filter {
            (includeArchived || !it.archived || it.id in selected) &&
                (category == "全部" || it.category == category) &&
                (search.isBlank() || it.name.contains(search, true))
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Label("选择衣物", "${selected.size} SELECTED")
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty())
                TextButton(
                    onClick = { onSelect(emptyList()) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("清空", fontSize = 12.sp)
                }
        }
        Field("搜索衣物", search, { search = it })
        ChipRow(listOf("全部") + Categories, category) { category = it }
        if (source.isEmpty()) Text("暂无可选衣物，请先在衣橱录入。", color = Muted, fontSize = 13.sp)
        source.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    val checked = g.id in selected
                    Column(
                        Modifier.weight(1f)
                            .clip(SmallPanel)
                            .background(Color.White)
                            .border(
                                if (checked) 2.dp else 1.dp,
                                if (checked) Teal else Line,
                                SmallPanel,
                            )
                            .clickable {
                                onSelect(if (checked) selected - g.id else selected + g.id)
                            }
                    ) {
                        Box {
                            GarmentArt(g, Modifier.fillMaxWidth().aspectRatio(1f))
                            if (checked)
                                Box(
                                    Modifier.align(Alignment.TopEnd).background(Teal).padding(3.dp)
                                ) {
                                    Glyph("check", Modifier.size(16.dp))
                                }
                        }
                        Text(g.name, Modifier.padding(7.dp), fontSize = 11.sp, maxLines = 2)
                        Text(
                            if (g.archived) "已归档" else g.care.label,
                            Modifier.padding(start = 7.dp, bottom = 7.dp),
                            fontSize = 10.sp,
                            color = if (g.care == Care.READY && !g.archived) Muted else Pink,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun selectedIds(text: String) = text.split(',').mapNotNull { it.toLongOrNull() }

@Composable
private fun OutfitEditor(
    original: Outfit,
    state: AppState,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(original.id) { mutableStateOf(original.name) }
    var occasion by rememberSaveable(original.id) { mutableStateOf(original.occasion) }
    var notes by rememberSaveable(original.id) { mutableStateOf(original.notes) }
    var selection by
        rememberSaveable(original.id) { mutableStateOf(original.items.joinToString(",")) }
    val current =
        original.copy(
            name = name,
            occasion = occasion,
            notes = notes,
            items = selectedIds(selection),
        )
    EditorFrame(
        if (original.id == 0L) "新建搭配" else "编辑搭配",
        current != original,
        state.busy,
        onBack,
        { vm.perform("搭配已保存", onBack) { saveOutfit(current) } },
    ) {
        OutfitMontage(
            state.data.pieces(current.items),
            Modifier.fillMaxWidth().height(160.dp).clip(Panel),
        )
        Field("搭配名称 *", name, { name = it.take(40) })
        Label("穿着场景")
        ChipRow(listOf("日常", "通勤", "休闲", "运动", "约会"), occasion) { occasion = it }
        GarmentPicker(state.data, current.items, { selection = it.joinToString(",") })
        Field("搭配备注", notes, { notes = it.take(500) }, single = false)
    }
}

@Composable
private fun PlanEditor(original: Plan, state: AppState, vm: WardrobeViewModel, onBack: () -> Unit) {
    var date by rememberSaveable(original.id) { mutableStateOf(original.date) }
    var outfit by rememberSaveable(original.id) { mutableLongStateOf(original.outfitId) }
    var touched by rememberSaveable(original.id) { mutableStateOf(false) }
    var notes by rememberSaveable(original.id) { mutableStateOf(original.notes) }
    val selected = state.data.outfits.find { it.id == outfit }
    val current =
        original.copy(
            date = date,
            notes = notes,
            title =
                if (touched || original.id == 0L) selected?.name ?: original.title
                else original.title,
            items =
                if (touched || original.id == 0L) selected?.items ?: original.items
                else original.items,
            outfitId = outfit,
        )
    EditorFrame(
        if (original.id == 0L) "安排穿搭计划" else "调整穿搭计划",
        current != original,
        state.busy,
        onBack,
        { vm.perform("计划已保存", onBack) { savePlan(current) } },
    ) {
        DateField("计划日期", date, { date = it })
        Label("选择搭配")
        if (state.data.outfits.isEmpty()) Text("请先到搭配实验室保存一套搭配。", color = Pink, fontSize = 13.sp)
        state.data.outfits.forEach { o ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(SmallPanel)
                    .border(
                        if (outfit == o.id) 2.dp else 1.dp,
                        if (outfit == o.id) Teal else Line,
                        SmallPanel,
                    )
                    .background(Color.White)
                    .clickable {
                        outfit = o.id
                        touched = true
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutfitMontage(state.data.pieces(o.items), Modifier.size(90.dp, 64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${o.items.size} 件 · ${o.occasion}", color = Muted, fontSize = 12.sp)
                }
                if (outfit == o.id) Glyph("check", color = Teal)
            }
        }
        Field("计划备注", notes, { notes = it.take(500) }, single = false)
        Text("计划是对未来的安排。只有确认实际穿着后，才会生成记录并计入统计。", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun WearEditor(original: Wear, state: AppState, vm: WardrobeViewModel, onBack: () -> Unit) {
    var date by rememberSaveable(original.id, original.planId) { mutableStateOf(original.date) }
    var notes by rememberSaveable(original.id, original.planId) { mutableStateOf(original.notes) }
    var selection by
        rememberSaveable(original.id, original.planId) {
            mutableStateOf(original.items.joinToString(","))
        }
    var ask by remember { mutableStateOf(false) }
    val current = original.copy(date = date, notes = notes, items = selectedIds(selection))
    if (original.voided) {
        Column {
            DetailTop("已撤销的穿着记录", onBack)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(original.date, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                OutfitMontage(
                    state.data.pieces(original.items),
                    Modifier.fillMaxWidth().height(200.dp),
                )
                Text(state.data.pieces(original.items).joinToString("、") { it.name })
                Text(original.notes)
                Text("这条记录保留供查阅，已从统计中排除。", color = Pink)
            }
        }
        return
    }
    EditorFrame(
        if (original.id != 0L) "编辑实际穿着" else if (original.planId != null) "确认实际穿着" else "记录实际穿着",
        current != original,
        state.busy,
        onBack,
        { vm.perform("实际穿着已保存", onBack) { saveWear(current) } },
        "保存实际穿着",
    ) {
        DateField("实际穿着日期", date, { date = it }, true)
        Text("日期可为今天或过去；记录穿着不会自动改变衣物的洗护状态。", color = Muted, fontSize = 12.sp)
        GarmentPicker(
            state.data,
            current.items,
            { selection = it.joinToString(",") },
            includeArchived = original.id != 0L || original.planId != null,
        )
        Field("穿着备注", notes, { notes = it.take(500) }, single = false)
        if (original.id != 0L)
            TextButton(onClick = { ask = true }, enabled = !state.busy) {
                Text("撤销这条穿着记录", color = Pink)
            }
    }
    if (ask)
        ConfirmDialog("撤销这条记录？", "记录会保留并标为已撤销，统计将重新计算；洗护状态保持不变。", { ask = false }) {
            ask = false
            vm.perform("记录已撤销", onBack) { voidWear(original.id) }
        }
}

@Composable
private fun GarmentDetail(
    g: Garment,
    data: Wardrobe,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DetailTop("衣物档案", onBack, "ITEM / ${g.id.toString().padStart(3,'0')}")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GarmentArt(g, Modifier.fillMaxWidth().height(280.dp).clip(Panel)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.Black, fontSize = 25.sp)
                        Micro(
                            "${g.category} / ${g.color} / ${g.season}",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (g.archived) Tag("已归档") else StatusTag(g.care)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                            data.wearCount(g.id).toString() to "穿着次数",
                            "${data.idleDays(g)}天" to
                                if (data.wearCount(g.id) == 0) "距录入时间" else "距上次穿着",
                            g.location to "收纳位置",
                        )
                        .forEach { (n, t) ->
                            Column(
                                Modifier.weight(1f)
                                    .background(Color.White, SmallPanel)
                                    .padding(12.dp)
                            ) {
                                Micro(t)
                                Text(
                                    n,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                )
                            }
                        }
                }
            }
            item {
                Text(
                    "品牌：${g.brand.ifBlank{"未填写"}}    价格：${if(g.price>0) "¥ %.2f".format(g.price) else "未填写"}",
                    fontSize = 13.sp,
                    color = Muted,
                )
                if (g.notes.isNotBlank())
                    Text(g.notes, Modifier.padding(top = 10.dp), fontSize = 14.sp)
            }
            if (!g.archived)
                item {
                    Label("洗护状态")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Care.entries.forEach { c ->
                            Tag(
                                c.label,
                                g.care == c,
                                { vm.perform("洗护状态已更新") { setCare(g.id, c) } },
                            )
                        }
                    }
                }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "编辑资料",
                        { navigate("garment/${g.id}") },
                        Modifier.weight(1f),
                        secondary = true,
                        icon = "edit",
                    )
                    ActionButton(
                        if (g.archived) "恢复衣物" else "归档衣物",
                        { ask = true },
                        Modifier.weight(1f),
                        secondary = true,
                        icon = "archive",
                    )
                }
            }
            item { SectionTitle("最近穿着", "WEAR HISTORY") }
            val records = data.wears.filter { g.id in it.items }
            if (records.isEmpty()) item { Text("尚未记录实际穿着。", color = Muted, fontSize = 13.sp) }
            items(records.take(10), key = { it.id }) { w ->
                WearRow(w, data) { navigate("wear/${w.id}") }
            }
            item {
                SectionTitle("洗护变化", "CARE LOG", "全部历史") { navigate("carehistory/${g.id}") }
                val logs = data.careLogs.filter { it.garmentId == g.id }.take(3)
                if (logs.isEmpty()) Text("尚无洗护变化。", color = Muted, fontSize = 13.sp)
                logs.forEach {
                    Text(
                        "${it.time.take(16).replace('T',' ')}  ${it.before.label} → ${it.after.label}",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
    if (ask)
        ConfirmDialog(
            if (g.archived) "恢复这件衣物？" else "归档这件衣物？",
            if (g.archived) "衣物将重新出现在衣橱中。" else "衣物将从在用衣橱和洗护列表移出，穿着与洗护历史继续保留。",
            { ask = false },
        ) {
            ask = false
            vm.perform(if (g.archived) "衣物已恢复" else "衣物已归档") { archive(g.id, !g.archived) }
        }
}

@Composable
private fun OutfitDetail(
    o: Outfit,
    data: Wardrobe,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DetailTop("搭配档案", onBack, "LOOK / ${o.id.toString().padStart(2,'0')}")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OutfitMontage(
                    data.pieces(o.items),
                    Modifier.fillMaxWidth().height(230.dp).clip(Panel),
                )
            }
            item {
                Text(o.name, fontWeight = FontWeight.Black, fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Tag(o.occasion, active = true)
                if (o.notes.isNotBlank())
                    Text(o.notes, Modifier.padding(top = 12.dp), color = Muted)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "安排到日历",
                        { navigate("planoutfit/${o.id}") },
                        Modifier.weight(1f),
                        icon = "calendar",
                    )
                    ActionButton(
                        "记录已穿",
                        { navigate("wearoutfit/${o.id}") },
                        Modifier.weight(1f),
                        secondary = true,
                        icon = "check",
                    )
                }
            }
            item { SectionTitle("搭配单品", "${o.items.size} ITEMS") }
            items(data.pieces(o.items), key = { it.id }) { g ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color.White, SmallPanel)
                        .clickable { navigate("detail/${g.id}") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GarmentArt(g, Modifier.size(70.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(g.location, color = Muted, fontSize = 12.sp)
                    }
                    if (g.archived) Text("已归档", color = Pink, fontSize = 12.sp)
                    else StatusTag(g.care)
                }
            }
            item {
                Row {
                    TextButton(onClick = { navigate("outfit/${o.id}") }) { Text("编辑搭配") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { ask = true }) { Text("删除搭配", color = Pink) }
                }
            }
        }
    }
    if (ask)
        ConfirmDialog("删除这套搭配？", "已经保存的计划和实际穿着记录会保留。", { ask = false }) {
            ask = false
            vm.perform("搭配已删除", onBack) { deleteOutfit(o.id) }
        }
}
