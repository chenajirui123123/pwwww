package com.wardrobe.atelier.ui

import com.wardrobe.atelier.presentation.*

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.domain.*
import org.json.JSONArray
import org.json.JSONObject

private data class PhotoDraft(val path: String, val name: String = "", val category: String = "上装")

private fun decodeDrafts(text: String): List<PhotoDraft> =
    JSONArray(text).let { a ->
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                PhotoDraft(it.getString("path"), it.getString("name"), it.getString("category"))
            }
        }
    }

private fun encodeDrafts(drafts: List<PhotoDraft>) =
    JSONArray()
        .apply {
            drafts.forEach {
                put(
                    JSONObject()
                        .put("path", it.path)
                        .put("name", it.name)
                        .put("category", it.category)
                )
            }
        }
        .toString()

@Composable
fun BatchPhotoEntry(state: AppState, vm: WardrobeViewModel, onBack: () -> Unit) {
    var encoded by rememberSaveable { mutableStateOf("[]") }
    var category by rememberSaveable { mutableStateOf("上装") }
    var location by rememberSaveable { mutableStateOf("待放置") }
    var discard by remember { mutableStateOf(false) }
    val drafts = remember(encoded) { decodeDrafts(encoded) }
    fun update(next: List<PhotoDraft>) {
        encoded = encodeDrafts(next)
    }
    val back = {
        if (!state.busy) {
            if (drafts.isNotEmpty()) discard = true else onBack()
        }
    }
    BackHandler { back() }
    val chooser =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                if (drafts.size + uris.distinct().size > 20) vm.error("这批最多 20 张图片，可以先保存这一批。")
                else {

                    vm.importImages(uris, success = null, after = { imported -> update(drafts + imported.map { PhotoDraft(it, category = category) }) })
                }
            }
        }
    Column(Modifier.fillMaxSize().imePadding()) {
        DetailTop("一次记下几件", back, "A FEW PHOTOS. ONE QUICK STEP.")
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text(localized("一张照片，对应一件衣物。"), fontSize = 22.sp, fontWeight = FontWeight.Black) }
            item {
                Text(localized("可多选商品截图或相册照片。先统一位置，再按需要逐件改名字和类别；照片不会自动识别。每批最多 20 件。"),
                    fontSize = 13.sp,
                    color = Muted,
                    lineHeight = 21.sp,
                )
            }
            item {
                ActionButton(
                    if (drafts.isEmpty()) "选择多张照片" else "继续加照片（${drafts.size}/20）",
                    { chooser.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy && drafts.size < 20,
                    icon = "plus",
                )
            }
            item {
                Text(localized("这一批先放在哪里？"), fontWeight = FontWeight.Bold)
                ChipRow((listOf("待放置") + state.data.locations).distinct(), location) {
                    if (!state.busy) location = it
                }
            }
            item {
                AtelierTextField(
                    location,
                    { location = it.take(40) },
                    Modifier.fillMaxWidth(),
                    label = { Text(localized("统一收纳位置")) },
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            item {
                Text(localized("统一类别（也可逐件改）"), fontWeight = FontWeight.Bold)
                ChipRow(Categories, category) {
                    if (!state.busy) {
                        category = it
                        update(drafts.map { d -> d.copy(category = it) })
                    }
                }
            }
            if (drafts.isEmpty()) item { EmptyPanel("先从几张照片开始", "不用一次录完；名称、颜色、季节等都可以以后补。") }
            itemsIndexed(drafts, key = { _, d -> d.path }) { index, d ->
                Column(
                    Modifier.fillMaxWidth().background(Color.White, Panel).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GarmentArt(
                            Garment(
                                name = d.name.ifBlank { "第 ${index + 1} 件" },
                                category = d.category,
                                image = d.path,
                            ),
                            Modifier.size(92.dp).clip(SmallPanel),
                        )
                        Column(Modifier.weight(1f)) {
                            Micro("ITEM ${(index + 1).toString().padStart(2, '0')}")
                            Text(localized(d.category), fontWeight = FontWeight.Black, fontSize = 20.sp)
                            TextButton(
                                onClick = {
                                    vm.discardDraftImages(listOf(d.path), success = null, after = { update(drafts.filterNot { it.path == d.path }) })
                                },
                                enabled = !state.busy,
                            ) {
                                Text(localized("移除这张"), fontSize = 12.sp, color = Muted)
                            }
                        }
                    }
                    ChipRow(Categories, d.category) { chosen ->
                        if (!state.busy)
                            update(
                                drafts.map {
                                    if (it.path == d.path) it.copy(category = chosen) else it
                                }
                            )
                    }
                    AtelierTextField(
                        d.name,
                        { name ->
                            update(
                                drafts.map {
                                    if (it.path == d.path) it.copy(name = name.take(40)) else it
                                }
                            )
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text(localized("第 ${index + 1} 件名字（可不填）")) },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                }
            }
        }
        if (drafts.isNotEmpty())
            Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                ActionButton(
                    "保存这 ${drafts.size} 件衣物",
                    {
                        vm.saveQuickGarments(drafts.mapIndexed { index, d ->
                                    Garment(
                                        name = d.name.ifBlank { "${d.category} ${index + 1}" },
                                        category = d.category,
                                        image = d.path,
                                        location = location,
                                        color = "其他",
                                        season = "未标注",
                                    )
                                }, success = "已记下 ${drafts.size} 件衣物", after = onBack)
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy && location.isNotBlank(),
                    icon = "check",
                )
            }
    }
    if (discard)
        ConfirmDialog("离开这批录入？", "这批照片和资料还没有保存为衣物。原相册照片不会受影响。", { discard = false }) {
            vm.discardDraftImages(drafts.map { it.path }, success = null, after = onBack)
        }
}
