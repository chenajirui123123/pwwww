package com.wardrobe.atelier.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wardrobe.atelier.*
import com.wardrobe.atelier.R
import com.wardrobe.atelier.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun WardrobeApp(vm: WardrobeViewModel = viewModel()) {
    LanguagePreferences.initialize(androidx.compose.ui.platform.LocalContext.current)
    val state by vm.state.collectAsStateWithLifecycle()
    val sharedImage by vm.sharedImage.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var routeHistory by rememberSaveable { mutableStateOf("") }
    var routeInstance by rememberSaveable { mutableLongStateOf(0L) }
    var nextInstance by rememberSaveable { mutableLongStateOf(0L) }
    val screenStates = rememberSaveableStateHolder()
    val navigate: (String) -> Unit = { next ->
        if (route.isNotEmpty())
            routeHistory =
                (routeHistory.split('|').filter { it.isNotBlank() } + "$routeInstance@$route")
                    .joinToString("|")
        nextInstance += 1
        routeInstance = nextInstance
        route = next
    }
    val goBack: () -> Unit = {
        val history = routeHistory.split('|').filter { it.isNotBlank() }
        screenStates.removeState("${state.account?.id}/$routeInstance@$route")
        val previous = history.lastOrNull()
        route = previous?.substringAfter('@') ?: ""
        routeInstance = previous?.substringBefore('@')?.toLongOrNull() ?: 0L
        routeHistory = history.dropLast(1).joinToString("|")
    }
    var knownOwner by rememberSaveable { mutableStateOf<Long?>(null) }
    val snack = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snack.showSnackbar(localized(it))
            vm.clearMessage()
        }
    }
    LaunchedEffect(state.loaded, state.account?.id) {
        if (state.loaded && knownOwner != state.account?.id) {
            route = ""
            routeHistory = ""
            tab = 0
            knownOwner = state.account?.id
        }
    }
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
                state.account == null || route.isNotEmpty()
        }
    }
    AtelierTheme {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            if (!state.loaded)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            else if (state.account == null) LightWelcome(vm, state.busy, sharedImage != null)
            else {
                BackHandler(route.isNotEmpty()) { goBack() }
                Scaffold(
                    containerColor = Paper,
                    topBar = { if (route.isEmpty()) BrandBar { tab = 2 } },
                    bottomBar = {
                        if (route.isEmpty())
                            AtelierNavigation(tab) { next ->
                                if (next != tab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    tab = next
                                }
                            }
                    },
                    snackbarHost = {
                        SnackbarHost(
                            snack,
                            Modifier.padding(
                                bottom = if (route.isEmpty() && tab != 1) 0.dp else 80.dp
                            ),
                        ) { message ->
                            Snackbar(
                                message,
                                shape = SmallPanel,
                                containerColor = Ink,
                                contentColor = Color.White,
                                actionColor = Teal,
                            )
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        AnimatedContent(
                            targetState =
                                if (route.isEmpty()) "tab/$tab" else "$routeInstance@$route",
                            transitionSpec = {
                                val fromTab = initialState.substringAfter("tab/", "").toIntOrNull()
                                val toTab = targetState.substringAfter("tab/", "").toIntOrNull()
                                val direction =
                                    if (fromTab != null && toTab != null) {
                                        if (toTab > fromTab) 1 else -1
                                    } else if (
                                        toTab != null ||
                                            ((targetState.substringBefore('@').toLongOrNull()
                                                ?: Long.MAX_VALUE) <
                                                (initialState.substringBefore('@').toLongOrNull()
                                                    ?: 0L))
                                    )
                                        -1
                                    else 1
                                ((fadeIn(tween(230, delayMillis = 35)) +
                                        slideInHorizontally(
                                            tween(300, easing = FastOutSlowInEasing)
                                        ) {
                                            direction * it / 9
                                        }) togetherWith
                                        (fadeOut(tween(130)) +
                                            slideOutHorizontally(
                                                tween(220, easing = FastOutSlowInEasing)
                                            ) {
                                                -direction * it / 14
                                            }))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "page-transition",
                        ) { destination ->
                            screenStates.SaveableStateProvider(
                                "${state.account?.id}/$destination"
                            ) {
                                if (destination.startsWith("tab/"))
                                    when (destination.substringAfter('/').toInt()) {
                                        0 -> FindWardrobeScreen(state.data, vm, navigate)
                                        1 -> InspirationScreen(state, vm, navigate)
                                        else -> LightProfile(state, vm, navigate)
                                    }
                                else
                                    DetailRouter(
                                        destination.substringAfter('@'),
                                        state,
                                        vm,
                                        goBack,
                                        navigate,
                                    )
                            }
                        }

                        if (state.busy)
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                color = Teal,
                                trackColor = Color.Transparent,
                            )
                    }
                }
            }
            sharedImage?.let { uri ->
                if (state.account != null)
                    AlertDialog(
                        onDismissRequest = { if (!state.busy) vm.consumeSharedImage(uri) },
                        shape = Panel,
                        title = { Text(localized("用这张图片记一件衣服"), fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "存入：${state.account?.nickname}。图片会放入新衣物草稿，补上类别和位置后再保存。当前未完成的页面会保留。"
                            )
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { vm.consumeSharedImage(uri) },
                                enabled = !state.busy,
                            ) {
                                Text(localized("暂时不录入"))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    var path = ""
                                    vm.perform(
                                        null,
                                        {
                                            vm.consumeSharedImage(uri)
                                            navigate("shared/${android.net.Uri.encode(path)}")
                                        },
                                    ) {
                                        path = importImage(uri)
                                    }
                                },
                                enabled = !state.busy,
                            ) {
                                Text(localized(if (state.busy) "正在读取…" else "开始录入"))
                            }
                        },
                    )
            }
            if (state.error != null)
                AlertDialog(
                    onDismissRequest = vm::clearError,
                    shape = Panel,
                    containerColor = Color.White,
                    title = { Text(localized("请检查一下"), fontWeight = FontWeight.Bold) },
                    text = { Text(localized(state.error!!)) },
                    confirmButton = { TextButton(onClick = vm::clearError) { Text(localized("知道了")) } },
                )
        }
    }
}

@Composable
private fun BrandBar(onProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paper).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(35.dp).background(Ink, Panel), contentAlignment = Alignment.Center) {
                Glyph("hanger", Modifier.size(23.dp), Teal)
            }
            Spacer(Modifier.width(9.dp))
            Text(localized("衣序"), fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-.6).sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Micro("YIXU / LIGHT", Ink)
                Micro("LESS INPUT. MORE IDEAS.", Muted)
            }
            IconTap("user", "个人中心", onProfile)
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Line)
    }
}

@Composable
fun AuthScreen(vm: WardrobeViewModel, busy: Boolean) {
    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("hanger", Modifier.size(28.dp), Ink)
            Spacer(Modifier.width(10.dp))
            Text(localized("衣序"), fontWeight = FontWeight.Black, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            Numeral("01", size = 32.sp, color = DeepTeal)
        }
        Hero(large = true)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(localized(if (register) "建立你的衣橱" else "欢迎回到衣序"),
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Micro("PERSONAL WARDROBE / OFFLINE EDITION", DeepTeal)
        }
        Text(localized("从一件衣物开始，整理属于你的日常。"), color = Muted, fontSize = 13.sp)
        AtelierTextField(
            username,
            { username = it },
            label = { Text(localized("账号")) },
            placeholder = { Text(localized("3–24 位字母、数字或下划线")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = SmallPanel,
        )
        if (register)
            AtelierTextField(
                nickname,
                { nickname = it },
                label = { Text(localized("昵称")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = SmallPanel,
            )
        AtelierTextField(
            password,
            { password = it },
            label = { Text(localized("密码")) },
            placeholder = { Text(localized("至少 8 位字符")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = SmallPanel,
        )
        ActionButton(
            if (busy) "正在打开衣橱…" else if (register) "注册并进入" else "登录",
            {
                vm.perform("欢迎来到衣序") {
                    if (register) register(username, password, nickname)
                    else login(username, password)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !busy,
            icon = "arrow",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { register = !register }, enabled = !busy) {
                Text(localized(if (register) "已有账号？去登录" else "还没有账号？创建一个"))
            }
            TextButton(onClick = { vm.perform("已打开示例衣橱") { demo() } }, enabled = !busy) {
                Text(localized("体验示例衣橱"), color = Pink)
            }
        }
        Text(localized("账号和照片仅保存在这台设备。示例衣橱独立存储，可随时切换到自己的账号。"),
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PageHeading(
    title: String,
    code: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    ModuleHeading(title, code, action, onAction)
}

@Composable
fun ClosetScreen(data: Wardrobe, navigate: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var season by rememberSaveable { mutableStateOf("全部季节") }
    var color by rememberSaveable { mutableStateOf("全部颜色") }
    var location by rememberSaveable { mutableStateOf("全部位置") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    val filtered =
        data.garments.filter {
            it.archived == archived &&
                (category == "全部" || it.category == category) &&
                (season == "全部季节" || it.season == season) &&
                (color == "全部颜色" || it.color == color) &&
                (location == "全部位置" || it.location == location) &&
                (search.isBlank() ||
                    "${it.name} ${it.brand} ${it.location} ${it.color}"
                        .contains(search.trim(), true))
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading("我的衣橱", "01 / COLLECTION", "录入衣物") { navigate("garment/0") } }
        item { Hero() }
        item {
            CollectionMetrics(
                listOf(
                    data.active.size to "衣物\n收藏",
                    data.active.count { it.care == Care.READY } to "随时\n可穿",
                    data.outfits.size to "灵感\n搭配",
                )
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtelierTextField(
                    search,
                    { search = it },
                    placeholder = { Text(localized("搜索衣物、品牌或位置"), fontSize = 13.sp) },
                    leadingIcon = { Glyph("search", Modifier.size(19.dp), Muted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = SmallPanel,
                )
                IconTap("filter", "筛选衣物", { filters = !filters }, if (filters) Pink else Ink)
            }
        }
        item { ChipRow(listOf("全部") + Categories, category) { category = it } }
        if (filters)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(listOf("全部季节") + Seasons, season) { season = it }
                    ChipRow(listOf("全部颜色") + PaletteNames, color) { color = it }
                    ChipRow(listOf("全部位置") + data.locations, location) { location = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(localized("查看已归档衣物"), fontSize = 13.sp)
                        Switch(archived, { archived = it })
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                category = "全部"
                                season = "全部季节"
                                color = "全部颜色"
                                location = "全部位置"
                                archived = false
                                search = ""
                            }
                        ) {
                            Text(localized("重置筛选"))
                        }
                    }
                }
            }
        item {
            Row {
                CodeLabel(
                    "${filtered.size} " + if (archived) "ARCHIVED ITEMS" else "COLLECTION ITEMS"
                )
                Spacer(Modifier.weight(1f))
                Micro("按录入时间 ↓")
            }
        }
        if (filtered.isEmpty())
            item {
                EmptyPanel(
                    "这里还没有衣物",
                    if (search.isNotBlank() || category != "全部") "试试其他关键词或筛选条件"
                    else "录入第一件，开启你的有序衣橱",
                    if (!archived) "录入衣物" else null,
                ) {
                    navigate("garment/0")
                }
            }
        items(filtered.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { g ->
                    GarmentCard(
                        g,
                        data.wearCount(g.id),
                        { navigate("detail/${g.id}") },
                        Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun OutfitMontage(items: List<Garment>, modifier: Modifier = Modifier) {
    OutfitBoard(items, modifier)
}

@Composable
fun OutfitsScreen(data: Wardrobe, navigate: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部") }
    val outfits = data.outfits.filter { filter == "全部" || it.occasion == filter }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageHeading("搭配实验室", "02 / OUTFIT STUDIO", "新建搭配") { navigate("outfit/0") } }
        item {
            EditorialBanner(
                "熟悉的衣物\n新的组合",
                "每一套搭配，都是你的灵感。",
                "COMPOSE YOUR LOOK",
                R.drawable.studio_stage,
            )
        }
        item { ChipRow(listOf("全部", "日常", "通勤", "休闲", "运动", "约会"), filter) { filter = it } }
        if (outfits.isEmpty())
            item { EmptyPanel("留住你的穿搭灵感", "从衣橱中挑选衣物，组成自己的搭配", "创建搭配") { navigate("outfit/0") } }
        items(outfits, key = { it.id }) { o ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(Panel)
                    .background(Color.White)
                    .border(1.dp, Line, Panel)
                    .clickable { navigate("outfitdetail/${o.id}") }
            ) {
                Box {
                    OutfitMontage(data.pieces(o.items), Modifier.fillMaxWidth().height(176.dp))
                    Box(Modifier.matchParentSize().registrationMarks())
                    Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        Tag(o.occasion, active = true, color = Teal)
                    }
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Micro("LOOK", Ink)
                        Spacer(Modifier.width(4.dp))
                        Numeral(o.id.toString().padStart(2, '0'), size = 32.sp)
                    }
                }
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(localized("${o.items.size} 件单品 · ${if(data.pieces(o.items).all{it.care==Care.READY&&!it.archived}) "随时可穿" else "查看衣物状态"}"),
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

@Composable
fun CalendarScreen(data: Wardrobe, navigate: (String) -> Unit, vm: WardrobeViewModel) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(date)
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val plans = data.plans.filter { it.date == date }
    val wears = data.wears.filter { it.date == date && !it.voided }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading("穿搭日历", "03 / DAILY PLANNER", "安排计划") { navigate("plan/0/$date") } }
        item {
            Column(
                Modifier.background(Color.White, Panel).border(1.dp, Line, Panel).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTap("back", "上个月", { monthText = month.minusMonths(1).toString() })
                    Column(Modifier.weight(1f)) {
                        Micro("${month.year} / CALENDAR")
                        Text(localized("${month.monthValue} 月"),
                            fontWeight = FontWeight.Black,
                            fontSize = 23.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            monthText = YearMonth.now().toString()
                            date = LocalDate.now().toString()
                        }
                    ) {
                        Text(localized("今天"), color = Pink)
                    }
                    IconTap("arrow", "下个月", { monthText = month.plusMonths(1).toString() })
                }
                Row {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Box(
                            Modifier.weight(1f).height(34.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(localized(it), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = (0 until ((offset + month.lengthOfMonth() + 6) / 7) * 7).toList()
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { cell ->
                            val day = cell - offset + 1
                            val valid = day in 1..month.lengthOfMonth()
                            val d = if (valid) month.atDay(day).toString() else ""
                            Box(
                                Modifier.weight(1f)
                                    .height(47.dp)
                                    .padding(2.dp)
                                    .clip(SmallPanel)
                                    .background(if (d == date) Ink else Color.Transparent)
                                    .then(if (valid) Modifier.clickable { date = d } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (valid)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(localized(day.toString()),
                                            fontFamily = TechnicalFont,
                                            fontWeight =
                                                if (d == date || d == LocalDate.now().toString())
                                                    FontWeight.Bold
                                                else FontWeight.Normal,
                                            color =
                                                if (d == date) Color.White
                                                else if (d == LocalDate.now().toString()) Pink
                                                else Ink,
                                            fontSize = 20.sp,
                                        )
                                        Row(
                                            Modifier.height(7.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            if (data.plans.any { it.date == d })
                                                Box(
                                                    Modifier.size(4.dp)
                                                        .background(Teal, CircleShape)
                                                )
                                            if (data.wears.any { it.date == d && !it.voided })
                                                Box(
                                                    Modifier.size(4.dp)
                                                        .background(Pink, CircleShape)
                                                )
                                        }
                                    }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(DeepTeal, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(localized("计划"), fontSize = 11.sp, color = Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(Pink, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(localized("实际穿着"), fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
        item {
            SectionTitle("${selected.monthValue} 月 ${selected.dayOfMonth} 日", "${plans.size} PLANS")
        }
        if (plans.isEmpty())
            item {
                Text(localized("这天还没有计划，给日常一点期待。"),
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        items(plans, key = { it.id }) { p ->
            val existing = data.wears.find { it.planId == p.id }
            Column(
                Modifier.background(Color.White, Panel).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(34.dp).background(Teal))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(localized("${p.items.size} 件单品 · 计划"), color = Muted, fontSize = 12.sp)
                    }
                    IconTap("edit", "调整计划", { navigate("plan/${p.id}/$date") })
                    IconTap("close", "删除计划", { deleteId = p.id })
                }
                if (p.notes.isNotBlank()) Text(p.notes, color = Muted, fontSize = 12.sp)
                Text(
                    data.pieces(p.items).joinToString(" · ") { it.name },
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.pieces(p.items).any { it.care != Care.READY || it.archived })
                    Text(localized("部分衣物待洗、清洗中或已归档，请确认实际穿着。"), color = Pink, fontSize = 12.sp)
                ActionButton(
                    if (existing == null) "确认实际穿着" else if (existing.voided) "穿着记录已撤销" else "已记录穿着",
                    { navigate("confirm/${p.id}") },
                    Modifier.fillMaxWidth(),
                    secondary = existing != null,
                    enabled = existing == null,
                    icon = if (existing == null) "check" else "clock",
                )
            }
        }
        item { SectionTitle("实际穿着", "${wears.size} RECORDS", "手动记录") { navigate("wear/0/$date") } }
        if (wears.isEmpty())
            item { Text(localized("保存计划不会增加穿着次数，确认已穿后才计入统计。"), fontSize = 12.sp, color = Muted) }
        items(wears, key = { it.id }) { w -> WearRow(w, data) { navigate("wear/${w.id}/$date") } }
    }
    if (deleteId != null)
        ConfirmDialog("删除这条计划？", "删除后，已经生成的实际穿着记录仍会保留。", { deleteId = null }) {
            val id = deleteId!!
            deleteId = null
            vm.perform("计划已删除") { deletePlan(id) }
        }
}

@Composable
fun WearRow(w: Wear, data: Wardrobe, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(SmallPanel)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val first = data.pieces(w.items).firstOrNull()
        if (first != null) GarmentArt(first, Modifier.size(54.dp).clip(SmallPanel))
        else Glyph("outfit", Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(localized(w.date), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                data.pieces(w.items).joinToString("、") { it.name },
                fontSize = 12.sp,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (w.notes.isNotBlank())
                Text(
                    w.notes,
                    fontSize = 11.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        if (w.voided) Text(localized("已撤销"), color = Pink, fontSize = 11.sp)
        else Glyph("arrow", Modifier.size(18.dp))
    }
}

@Composable
fun CareScreen(data: Wardrobe, navigate: (String) -> Unit, vm: WardrobeViewModel) {
    var selected by rememberSaveable { mutableStateOf(Care.DIRTY.name) }
    val state = Care.valueOf(selected)
    val garments = data.active.filter { it.care == state }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading("洗护工作台", "04 / CARE STATION") }
        item {
            EditorialBanner(
                "照顾衣物\n照顾日常",
                "${data.active.count { it.care != Care.READY }} 件有待处理的手动标记",
                "CARE & RENEW",
                R.drawable.care_stage,
            )
        }
        item {
            ChipRow(
                Care.entries.map { "${it.label} ${data.active.count { g -> g.care == it }}" },
                "${state.label} ${data.active.count { it.care == state }}",
            ) { label ->
                selected = Care.entries.first { label.startsWith(it.label) }.name
            }
        }
        item {
            SectionTitle(state.label, "${garments.size} ITEMS", "洗护历史") { navigate("carehistory") }
        }
        if (garments.isEmpty())
            item {
                EmptyPanel(
                    "${state.label}列表为空",
                    if (state == Care.DIRTY) "没有衣物被手动标记为待洗。" else "更新衣物状态后会出现在这里。",
                )
            }
        items(garments, key = { it.id }) { g ->
            Row(
                Modifier.fillMaxWidth().background(Color.White, Panel).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GarmentArt(
                    g,
                    Modifier.size(74.dp).clip(SmallPanel).clickable { navigate("detail/${g.id}") },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                    Text(g.location, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            vm.perform("洗护状态已更新") {
                                setCare(
                                    g.id,
                                    when (state) {
                                        Care.DIRTY -> Care.WASHING
                                        Care.WASHING -> Care.READY
                                        Care.READY -> Care.DIRTY
                                    },
                                )
                            }
                        },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(localized(when (state) {
                                Care.DIRTY -> "开始清洗 →"
                                Care.WASHING -> "洗好了，恢复可穿 →"
                                Care.READY -> "标记为待洗 →"
                            }),
                            color = if (state == Care.DIRTY) Pink else Ink,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(data: Wardrobe, navigate: (String) -> Unit) {
    var period by rememberSaveable { mutableStateOf("近30天") }
    var idle by rememberSaveable { mutableStateOf("30天") }
    val since = if (period == "近30天") LocalDate.now().minusDays(29).toString() else "0000"
    val wears = data.wears.filter { !it.voided && it.date >= since }
    val active = data.active
    val worn = active.count { g -> wears.any { g.id in it.items } }
    val percentage = if (active.isEmpty()) 0 else worn * 100 / active.size
    val frequencies =
        active
            .map { g -> g to wears.count { g.id in it.items } }
            .sortedWith(
                compareByDescending<Pair<Garment, Int>> { it.second }.thenBy { it.first.id }
            )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading("衣橱观察", "05 / INSIGHTS") }
        item { ChipRow(listOf("近30天", "全部时间"), period) { period = it } }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(Panel)
                    .background(Ink)
                    .registrationMarks(Color.White.copy(alpha = .16f))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                    UtilizationRing(percentage / 100f, Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Numeral("$percentage%", size = 34.sp, color = Color.White)
                        Micro("RECORDED", Teal)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Micro("穿着记录覆盖", Color(0xFFBBC9C5))
                    Numeral("$worn / ${active.size}", size = 38.sp, color = Color.White)
                    Text(localized("${period}有穿着记录的衣物"), color = Color(0xFFBBC9C5), fontSize = 11.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                        wears.size.toString() to "穿着记录",
                        data.active.size.toString() to "在用衣物",
                        data.garments.count { it.archived }.toString() to "归档保留",
                    )
                    .forEach { (n, t) ->
                        Column(
                            Modifier.weight(1f).background(Color.White, SmallPanel).padding(14.dp)
                        ) {
                            Micro(t)
                            Numeral(n, size = 34.sp)
                        }
                    }
            }
        }
        item { SectionTitle("类别分布", "CATEGORY MIX") }
        item {
            Column(
                Modifier.background(Color.White, Panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Categories.forEach { c ->
                    val n = active.count { it.category == c }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(localized(c), fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        RatioBar(
                            if (active.isEmpty()) 0f else n.toFloat() / active.size,
                            Modifier.weight(1f).height(6.dp),
                        )
                        Text(localized(n.toString()),
                            Modifier.width(30.dp).padding(start = 10.dp),
                            fontFamily = TechnicalFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(localized("类别和记录覆盖仅统计未归档衣物。"), fontSize = 11.sp, color = Muted)
            }
        }
        item { SectionTitle("记录较多的单品", "MOST RECORDED") }
        if (frequencies.none { it.second > 0 })
            item { Text(localized("主动记录穿着后，这里会展示登记次数。"), color = Muted, fontSize = 13.sp) }
        items(frequencies.filter { it.second > 0 }.take(5), key = { it.first.id }) { (g, n) ->
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White, SmallPanel)
                    .clickable { navigate("detail/${g.id}") }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GarmentArt(g, Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Text(g.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Numeral("$n", size = 30.sp, color = Pink)
                Text(localized(" 次"), fontSize = 12.sp, color = Muted)
            }
        }
        item {
            SectionTitle("重新发现", "REDISCOVER")
            ChipRow(listOf("30天", "60天", "90天"), idle) { idle = it }
            Spacer(Modifier.height(8.dp))
            Text(localized("距离最后记录的天数；没有记录时从录入日计算。没有登记不代表没有穿过。"), fontSize = 11.sp, color = Muted)
        }
        val forgotten =
            active
                .filter { data.idleDays(it) >= idle.removeSuffix("天").toInt() }
                .sortedByDescending { data.idleDays(it) }
        if (forgotten.isEmpty())
            item { Text(localized("没有超过 $idle 未更新穿着记录的衣物。"), fontSize = 13.sp, color = Muted) }
        items(forgotten, key = { it.id }) { g ->
            Row(
                Modifier.fillMaxWidth()
                    .background(Color.White, SmallPanel)
                    .clickable { navigate("detail/${g.id}") }
                    .padding(12.dp)
            ) {
                Text(g.name, Modifier.weight(1f), fontSize = 14.sp)
                Text(localized("${data.idleDays(g)} 天无记录"), color = Pink, fontSize = 12.sp)
            }
        }
        item {
            Text(localized("统计以有效实际穿着记录为依据，计划不计入次数。撤销记录后会即时重算，历史不会自动过期。"),
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Panel,
        containerColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp, 4.dp).background(Teal))
                Text(localized(title), fontWeight = FontWeight.Black, fontSize = 23.sp)
            }
        },
        text = { Text(localized(message)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localized("取消")) } },
        confirmButton = {
            ActionButton("确认", onConfirm, Modifier.widthIn(min = 108.dp), icon = "check")
        },
    )
}
