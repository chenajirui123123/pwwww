package com.wardrobe.atelier.ui

import com.wardrobe.atelier.presentation.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
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
    var filters by rememberSaveable { mutableStateOf(false) }
    val presenter = rememberInspirationPresenter(seed)
    val suggestion by presenter.state.collectAsStateWithLifecycle()
    LaunchedEffect(presenter, data.garments) { presenter.updateInventory(data.garments) }
    val (season, structure, mood, outer, bag) = suggestion.request
    val locked = suggestion.locked
    val excluded = suggestion.excluded
    val pieces = data.pieces(suggestion.picked)
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
                        ChipRow(listOf("不限", "春秋", "夏季", "冬季"), season) { value ->
                            presenter.changeRequest { it.copy(season = value) }
                        }
                        Text(localized("组合方式"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ChipRow(listOf("自动", "上下装", "连衣裙"), structure) { value ->
                            presenter.changeRequest { it.copy(structure = value) }
                        }
                        Text(localized("款式倾向"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        ChipRow(listOf("随心", "通勤", "休闲", "运动"), mood) { value ->
                            presenter.changeRequest { it.copy(mood = value) }
                        }
                        Row {
                            CheckOption(
                                "搭上外套",
                                outer,
                                { value ->
                                    presenter.changeRequest { it.copy(outerwear = value) }
                                },
                                Modifier.weight(1f),
                            )
                            CheckOption(
                                "配个包",
                                bag,
                                { value ->
                                    presenter.changeRequest { it.copy(bag = value) }
                                },
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (suggestion.pending) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal) }
            if (suggestion.problem.isNotBlank())
                item {
                    Text(localized(suggestion.problem),
                        Modifier.fillMaxWidth()
                            .background(Lime.copy(alpha = .35f), SmallPanel)
                            .padding(13.dp),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            if (locked.isNotEmpty() || excluded.isNotEmpty())
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(localized("锁定 ${locked.size} 件 · 本轮跳过 ${excluded.size} 件"),
                            Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = DeepTeal,
                        )
                        TextButton(onClick = presenter::resetConstraints) {
                            Text(localized("重置"))
                        }
                    }
                }
            if (pieces.isEmpty()) {
                if (!suggestion.pending) item {
                    EmptyPanel("先留一点衣物线索", "有上衣和下装，或一件连衣裙，就能开始组合。", "录入衣物") {
                        navigate("garment/0")
                    }
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().clip(Panel).background(Color.White)) {
                        OutfitBoard(pieces, Modifier.fillMaxWidth().height(boardHeight), locked, presenter::toggleLock)
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
                item {
                    Text(localized("点衣物可锁定 · " + suggestion.explanation),
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
                                onClick = { presenter.toggleLock(g.id) },
                                enabled = !suggestion.pending,
                            ) {
                                Text(localized(if (g.id in locked) "已锁定" else "锁住这件"))
                            }
                        }
                        Row {
                            TextButton(
                                onClick = { presenter.replacePiece(g.id) },
                                enabled = !suggestion.pending && g.id !in locked,
                            ) {
                                Text(localized("换这一件"))
                            }
                            TextButton(
                                onClick = { presenter.skip(g.id) },
                                enabled = !suggestion.pending,
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
                    presenter::reroll,
                    Modifier.weight(1f),
                    enabled = !state.busy && !suggestion.pending,
                    icon = "refresh",
                )
                ActionButton(
                    if (saved) "已收藏" else "收藏这套",
                    {
                        vm.saveOutfit(Outfit(
                                    name =
                                        "${if (mood == "随心") "日常" else mood}灵感 ${data.outfits.size + 1}",
                                    occasion = if (mood == "随心") "日常" else mood,
                                    notes = "衣橱灵感组合",
                                    items = pieces.map { it.id },
                                ), success = "搭配已收藏", after = {})
                    },
                    Modifier.weight(1f),
                    secondary = true,
                    enabled = !saved && !state.busy && !suggestion.pending,
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
            vm.deleteOutfit(o.id, success = "已删除收藏", after = onBack)
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
