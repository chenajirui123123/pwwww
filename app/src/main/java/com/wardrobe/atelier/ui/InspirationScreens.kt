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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.AppState
import com.wardrobe.atelier.WardrobeViewModel
import com.wardrobe.atelier.domain.*

private fun idSet(text: String): Set<Long> =
    text.split(',').mapNotNull { it.toLongOrNull() }.toSet()

@Composable
fun InspirationScreen(
    state: AppState,
    vm: WardrobeViewModel,
    navigate: (String) -> Unit,
    seed: Long = 0,
    onBack: (() -> Unit)? = null,
) {
    val data = state.data
    val boardHeight = if (LocalConfiguration.current.screenHeightDp < 760) 210.dp else 250.dp
    var season by rememberSaveable { mutableStateOf("不限") }
    var structure by rememberSaveable { mutableStateOf("自动") }
    var mood by rememberSaveable { mutableStateOf("随心") }
    var outer by rememberSaveable { mutableStateOf(false) }
    var bag by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var lockedText by rememberSaveable(seed) { mutableStateOf(if (seed > 0) "$seed" else "") }
    var excludedText by rememberSaveable { mutableStateOf("") }
    var pickedText by rememberSaveable { mutableStateOf("") }
    var seenText by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var inventoryRevision by rememberSaveable { mutableStateOf<Int?>(null) }
    val locked = idSet(lockedText)
    val excluded = idSet(excludedText)
    val pieces = data.pieces(idSet(pickedText).toList())
    val request = InspirationRequest(season, structure, mood, outer, bag)
    fun generate(
        req: InspirationRequest = request,
        pins: Set<Long> = locked,
        skips: Set<Long> = excluded,
        replace: Long? = null,
    ) {
        val effectivePins = if (replace != null) pieces.map { it.id }.toSet() - replace else pins
        val effectiveSkips = if (replace != null) skips + replace else skips
        val effectiveRequest =
            if (replace != null)
                req.copy(structure = if (pieces.any { it.category == "连衣裙" }) "连衣裙" else "上下装")
            else req
        runCatching {
                InspirationEngine.generate(
                    data.garments,
                    effectiveRequest,
                    effectivePins,
                    effectiveSkips,
                    seenText.split(';').filter { it.isNotBlank() }.toSet(),
                )
            }
            .onSuccess { result ->
                pickedText = result.items.joinToString(",") { it.id.toString() }
                seenText =
                    (seenText.split(';').filter { it.isNotBlank() } + result.signature)
                        .takeLast(24)
                        .joinToString(";")
                explanation = result.notes.joinToString(" · ")
                problem = if (result.repeated) "当前条件下的组合已经看过了，可以换个条件，或解锁一件再试。" else ""
            }
            .onFailure { e ->
                problem = if (replace != null) "这一件暂时没有合适的替换。其他单品保留原样。" else e.message ?: "暂时无法组合"
                if (replace == null) pickedText = ""
            }
    }
    // A saved suggestion survives tab switches, but never outlives changed inventory constraints.
    val revision =
        data.garments
            .map { listOf(it.id, it.category, it.color, it.season, it.style, it.archived) }
            .hashCode()
    LaunchedEffect(revision, seed) {
        if (inventoryRevision != revision || pickedText.isBlank()) generate()
        inventoryRevision = revision
    }
    val saved =
        data.outfits.any {
            it.items.toSet() == pieces.map { g -> g.id }.toSet() && pieces.isNotEmpty()
        }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) DetailTop("用这件，搭一套", onBack, "START WITH ONE PIECE")
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                if (onBack == null) ModuleHeading("今天，换种组合", "02 / A LITTLE SURPRISE")
                Text(localized("锁住喜欢的单品，其余交给一点随机。"),
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { filters = !filters }) {
                        Glyph("filter", Modifier.size(18.dp), DeepTeal)
                        Spacer(Modifier.width(6.dp))
                        Text(localized(if (filters) "收起搭配条件" else "搭配条件"))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { navigate("saved") }) { Text(localized("我的收藏 →")) }
                }
                RevealPanel(filters) {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, Panel).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(localized("季节"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ChipRow(listOf("不限", "春秋", "夏季", "冬季"), season) {
                            season = it
                            generate(request.copy(season = it))
                        }
                        Text(localized("组合方式"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ChipRow(listOf("自动", "上下装", "连衣裙"), structure) {
                            structure = it
                            generate(request.copy(structure = it))
                        }
                        Text(localized("款式倾向"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ChipRow(listOf("随心", "通勤", "休闲", "运动"), mood) {
                            mood = it
                            generate(request.copy(mood = it))
                        }
                        Row {
                            CheckOption(
                                "搭上外套",
                                outer,
                                {
                                    outer = it
                                    generate(request.copy(outerwear = it))
                                },
                                Modifier.weight(1f),
                            )
                            CheckOption(
                                "配个包",
                                bag,
                                {
                                    bag = it
                                    generate(request.copy(bag = it))
                                },
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (problem.isNotBlank())
                item {
                    Text(localized(problem),
                        Modifier.fillMaxWidth()
                            .background(Lime.copy(alpha = .35f), SmallPanel)
                            .padding(13.dp),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            if (pieces.isEmpty())
                item {
                    EmptyPanel("先留一点衣物线索", "有上衣和下装，或一件连衣裙，就能开始组合。", "录入衣物") {
                        navigate("garment/0")
                    }
                }
            else {
                item {
                    Column(Modifier.fillMaxWidth().clip(Panel).background(Color.White)) {
                        OutfitBoard(pieces, Modifier.fillMaxWidth().height(boardHeight), locked) {
                            id ->
                            lockedText =
                                (if (id in locked) locked - id else locked + id).joinToString(",")
                        }
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Micro("FOUND IN YOUR OWN WARDROBE", DeepTeal)
                                Text(localized("这一套，你已经拥有"),
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Numeral(pieces.size.toString().padStart(2, '0'), size = 38.sp)
                        }
                    }
                }
                if (locked.isNotEmpty() || excluded.isNotEmpty())
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(localized("锁定 ${locked.size} 件 · 本轮跳过 ${excluded.size} 件"),
                                Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = DeepTeal,
                            )
                            TextButton(
                                onClick = {
                                    lockedText = ""
                                    excludedText = ""
                                    generate(pins = emptySet(), skips = emptySet())
                                }
                            ) {
                                Text(localized("重置"))
                            }
                        }
                    }
                item {
                    Text(localized("点衣物可锁定 · " + explanation),
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    )
                }
                item {
                    ProfileRow("archive", "去哪里拿这一套", "按收纳位置列出拿衣清单") {
                        navigate("pickup/${pieces.joinToString(",") { it.id.toString() }}")
                    }
                }
                item { SectionTitle("按你的意思调整", "MAKE IT YOURS") }
                items(pieces, key = { it.id }) { g ->
                    Column(
                        Modifier.fillMaxWidth().background(Color.White, Panel).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GarmentArt(
                                g,
                                Modifier.size(64.dp).clip(SmallPanel).clickable {
                                    navigate("detail/${g.id}")
                                },
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(g.location, fontSize = 12.sp, color = DeepTeal)
                            }
                            TextButton(
                                onClick = {
                                    lockedText =
                                        (if (g.id in locked) locked - g.id else locked + g.id)
                                            .joinToString(",")
                                }
                            ) {
                                Text(localized(if (g.id in locked) "已锁定" else "锁住这件"))
                            }
                        }
                        Row {
                            TextButton(
                                onClick = { generate(replace = g.id) },
                                enabled = g.id !in locked,
                            ) {
                                Text(localized("换这一件"))
                            }
                            TextButton(
                                onClick = {
                                    val pins = locked - g.id
                                    val skips = excluded + g.id
                                    lockedText = pins.joinToString(",")
                                    excludedText = skips.joinToString(",")
                                    generate(pins = pins, skips = skips)
                                }
                            ) {
                                Text(localized("本轮不选"), color = Muted)
                            }
                        }
                    }
                }
                item {
                    Text(localized("依据你登记的类别、颜色、季节与款式组合。不会推断你穿过什么，也不要求维护洗护状态。"),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        color = Muted,
                    )
                }
            }
        }
        if (pieces.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionButton(
                    "换一套",
                    { generate() },
                    Modifier.weight(1f),
                    enabled = !state.busy,
                    icon = "refresh",
                )
                ActionButton(
                    if (saved) "已收藏" else "收藏这套",
                    {
                        vm.perform("搭配已收藏") {
                            saveOutfit(
                                Outfit(
                                    name =
                                        "${if (mood == "随心") "日常" else mood}灵感 ${data.outfits.size + 1}",
                                    occasion = if (mood == "随心") "日常" else mood,
                                    notes = "衣橱灵感组合",
                                    items = pieces.map { it.id },
                                )
                            )
                        }
                    },
                    Modifier.weight(1f),
                    secondary = true,
                    enabled = !saved && !state.busy,
                    icon = "check",
                )
            }
        }
    }
}

@Composable
fun SavedLooks(data: Wardrobe, onBack: () -> Unit, navigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailTop("收藏的搭配", onBack, "KEEP THE GOOD IDEAS")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ActionButton(
                    "自己组合一套",
                    { navigate("outfit/0") },
                    Modifier.fillMaxWidth(),
                    secondary = true,
                    icon = "plus",
                )
            }
            if (data.outfits.isEmpty()) item { EmptyPanel("还没有收藏", "在灵感页看到喜欢的组合，就留下来。") }
            items(data.outfits, key = { it.id }) { o ->
                Column(
                    Modifier.fillMaxWidth().clip(Panel).background(Color.White).clickable {
                        navigate("outfitdetail/${o.id}")
                    }
                ) {
                    OutfitMontage(data.pieces(o.items), Modifier.fillMaxWidth().height(170.dp))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(localized("${o.items.size} 件单品 · ${o.occasion}"),
                                fontSize = 12.sp,
                                color = Muted,
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
fun LightOutfitDetail(
    o: Outfit,
    data: Wardrobe,
    vm: WardrobeViewModel,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf(false) }
    val pieces = data.pieces(o.items)
    Column(Modifier.fillMaxSize()) {
        DetailTop("搭配档案", onBack, "AN IDEA WORTH KEEPING")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { OutfitMontage(pieces, Modifier.fillMaxWidth().height(230.dp).clip(Panel)) }
            item {
                Text(o.name, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(localized(o.occasion), color = Muted)
            }
            item {
                ActionButton(
                    "看看去哪里拿",
                    { navigate("pickup/${o.items.joinToString(",")}") },
                    Modifier.fillMaxWidth(),
                    icon = "archive",
                )
            }
            items(pieces, key = { it.id }) { g ->
                ProfileRow("hanger", g.name, g.location + if (g.archived) " · 已归档" else "") {
                    navigate("detail/${g.id}")
                }
            }
            if (o.notes.isNotBlank()) item { Text(o.notes, color = Muted) }
            item {
                ActionButton(
                    "调整这套搭配",
                    { navigate("outfit/${o.id}") },
                    Modifier.fillMaxWidth(),
                    secondary = true,
                )
            }
            item {
                Row {
                    TextButton(onClick = { navigate("planoutfit/${o.id}") }) { Text(localized("安排日期（可选）")) }
                    TextButton(onClick = { deleting = true }) { Text(localized("删除收藏"), color = Muted) }
                }
            }
        }
    }
    if (deleting)
        ConfirmDialog("删除这套收藏？", "衣物与已有历史记录不会删除。", { deleting = false }) {
            vm.perform("已删除收藏", onBack) { deleteOutfit(o.id) }
        }
}

@Composable
fun PickupChecklist(data: Wardrobe, ids: List<Long>, onBack: () -> Unit) {
    var checkedText by rememberSaveable(ids.joinToString(",")) { mutableStateOf("") }
    val pieces = data.pieces(ids).distinctBy { it.id }
    val checked = idSet(checkedText).intersect(pieces.map { it.id }.toSet())
    Column(Modifier.fillMaxSize()) {
        DetailTop("照着位置，顺手拿齐", onBack, "GRAB & GO")
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                CollectionMetrics(
                    listOf(
                        checked.size to "已经\n拿到",
                        pieces.size to "需要\n单品",
                        pieces.map { it.location }.distinct().size to "收纳\n位置",
                    )
                )
            }
            item {
                Text(localized("这是上次登记的位置。勾选只用于这份清单，不会生成穿着或洗护记录。"),
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                )
            }
            pieces
                .groupBy { it.location }
                .forEach { (place, garments) ->
                    item { SectionTitle(place, "${garments.size} ITEMS") }
                    items(garments, key = { it.id }) { g ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Color.White, SmallPanel)
                                .clickable {
                                    checkedText =
                                        (if (g.id in checked) checked - g.id else checked + g.id)
                                            .joinToString(",")
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(g.id in checked, null)
                            GarmentArt(g, Modifier.size(55.dp).clip(SmallPanel))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(g.name, fontWeight = FontWeight.Bold)
                                if (g.archived) Text(localized("已归档，拿之前确认一下"), color = Pink, fontSize = 12.sp)
                            }
                        }
                    }
                }
            if (pieces.isNotEmpty() && checked.size == pieces.size)
                item {
                    Text(localized("拿齐了，出门吧。"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = DeepTeal,
                    )
                }
            item { TextButton(onClick = { checkedText = "" }) { Text(localized("重新开始这份清单")) } }
        }
    }
}
