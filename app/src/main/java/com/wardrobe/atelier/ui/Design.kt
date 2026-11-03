package com.wardrobe.atelier.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.R
import com.wardrobe.atelier.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Ink = Color(0xFF20272A)
val Paper = Color(0xFFEEF0ED)
val Muted = Color(0xFF59676B)
val Teal = Color(0xFF28C4BD)
val Pink = Color(0xFFCE174C)
val Lime = Color(0xFFDCEB97)
val Line = Color(0xFFD9DFDB)
val Panel = CutCornerShape(topEnd = 12.dp, bottomStart = 4.dp)
val SmallPanel = CutCornerShape(topEnd = 7.dp, bottomStart = 2.dp)

@Composable
fun AtelierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = DeepTeal,
                onPrimary = Color.White,
                secondary = Teal,
                onSecondary = Ink,
                tertiary = Pink,
                background = Paper,
                surface = Color.White,
                surfaceVariant = Color(0xFFE6EAE6),
                onSurface = Ink,
                onSurfaceVariant = Muted,
                outline = Line,
                error = Pink,
            ),
        typography =
            Typography(
                bodyLarge =
                    androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                bodyMedium =
                    androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                titleLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            ),
        shapes =
            Shapes(
                extraSmall = SmallPanel,
                small = SmallPanel,
                medium = Panel,
                large = Panel,
                extraLarge = Panel,
            ),
        content = content,
    )
}

@Composable
fun Micro(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val latin = text.all { it.code < 128 }
    Text(
        text,
        modifier,
        fontSize = if (latin) 13.sp else 11.sp,
        lineHeight = 16.sp,
        fontFamily = if (latin) TechnicalFont else FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = if (latin) .25.sp else 0.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionTitle(
    text: String,
    code: String = "",
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(3.dp, 16.dp).background(Pink))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) { CodeLabel(code) }
            if (action != null)
                TextButton(onClick = onAction, contentPadding = PaddingValues(start = 8.dp)) {
                    Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Glyph("arrow", Modifier.size(14.dp), DeepTeal)
                }
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by
        animateFloatAsState(if (pressed) .965f else 1f, tween(110), label = "button-press")
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier =
            modifier.heightIn(min = 48.dp).graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            },
        enabled = enabled,
        shape = SmallPanel,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (secondary) Ink else Pink,
                disabledContainerColor = Line,
                disabledContentColor = Muted,
                contentColor = Color.White,
            ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        border = BorderStroke(1.dp, if (secondary) Ink else Pink.copy(alpha = .5f)),
    ) {
        if (icon != null) {
            Glyph(icon, Modifier.size(18.dp), Color.White)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun IconTap(name: String, label: String, onClick: () -> Unit, tint: Color = Ink) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Box(
            Modifier.size(34.dp)
                .background(tint.copy(alpha = .065f), SmallPanel)
                .border(1.dp, tint.copy(alpha = .10f), SmallPanel),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(name, Modifier.size(20.dp), tint)
        }
    }
}

@Composable
fun Glyph(name: String, modifier: Modifier = Modifier.size(24.dp), color: Color = Ink) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val s = Stroke(width = 1.8f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(color, Offset(x, y), Offset(x2, y2), 1.8f, StrokeCap.Square)
            fun path(vararg pts: Float) {
                val p = Path()
                p.moveTo(pts[0], pts[1])
                var i = 2
                while (i < pts.size) {
                    p.lineTo(pts[i], pts[i + 1])
                    i += 2
                }
                drawPath(p, color, style = s)
            }
            when (name) {
                "hanger" -> {
                    drawArc(color, 180f, 260f, false, Offset(9f, 2f), Size(6f, 6f), style = s)
                    path(12f, 8f, 12f, 10f, 2f, 17f, 2f, 19f, 22f, 19f, 22f, 17f, 12f, 10f)
                }
                "outfit" -> {
                    path(
                        8f,
                        3f,
                        5f,
                        4f,
                        1f,
                        9f,
                        5f,
                        12f,
                        7f,
                        9f,
                        7f,
                        21f,
                        17f,
                        21f,
                        17f,
                        9f,
                        19f,
                        12f,
                        23f,
                        9f,
                        19f,
                        4f,
                        16f,
                        3f,
                    )
                    drawArc(color, 0f, 180f, false, Offset(8f, 0f), Size(8f, 7f), style = s)
                }
                "calendar" -> {
                    drawRoundRect(
                        color,
                        Offset(3f, 5f),
                        Size(18f, 16f),
                        androidx.compose.ui.geometry.CornerRadius(2f),
                        style = s,
                    )
                    line(3f, 10f, 21f, 10f)
                    line(8f, 2f, 8f, 7f)
                    line(16f, 2f, 16f, 7f)
                    drawCircle(color, 1.2f, Offset(8f, 15f))
                    drawCircle(color, 1.2f, Offset(15f, 15f))
                }
                "care" -> {
                    val p = Path()
                    p.moveTo(12f, 2f)
                    p.cubicTo(8f, 8f, 4f, 11f, 4f, 15f)
                    p.cubicTo(4f, 24f, 20f, 24f, 20f, 15f)
                    p.cubicTo(20f, 11f, 16f, 8f, 12f, 2f)
                    drawPath(p, color, style = s)
                    path(8f, 15f, 11f, 18f, 16f, 12f)
                }
                "stats" -> {
                    line(4f, 20f, 21f, 20f)
                    line(6f, 16f, 6f, 11f)
                    line(12f, 16f, 12f, 4f)
                    line(18f, 16f, 18f, 8f)
                }
                "user" -> {
                    drawCircle(color, 4f, Offset(12f, 7f), style = s)
                    drawArc(color, 180f, 180f, false, Offset(4f, 14f), Size(16f, 12f), style = s)
                }
                "plus" -> {
                    line(12f, 4f, 12f, 20f)
                    line(4f, 12f, 20f, 12f)
                }
                "search" -> {
                    drawCircle(color, 6f, Offset(10f, 10f), style = s)
                    line(15f, 15f, 21f, 21f)
                }
                "back" -> path(15f, 4f, 7f, 12f, 15f, 20f)
                "arrow" -> {
                    path(9f, 5f, 16f, 12f, 9f, 19f)
                }
                "close" -> {
                    line(6f, 6f, 18f, 18f)
                    line(6f, 18f, 18f, 6f)
                }
                "check" -> path(4f, 12f, 9f, 17f, 20f, 6f)
                "edit" -> {
                    path(4f, 20f, 5f, 14f, 17f, 2f, 22f, 7f, 10f, 19f, 4f, 20f)
                    line(14f, 5f, 19f, 10f)
                }
                "filter" -> {
                    line(3f, 6f, 21f, 6f)
                    line(6f, 12f, 18f, 12f)
                    line(10f, 18f, 14f, 18f)
                }
                "archive" -> {
                    drawRect(color, Offset(3f, 4f), Size(18f, 5f), style = s)
                    path(5f, 9f, 5f, 21f, 19f, 21f, 19f, 9f)
                    line(10f, 13f, 14f, 13f)
                }
                "clock" -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = s)
                    path(12f, 6f, 12f, 12f, 16f, 14f)
                }
                else -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = s)
                    drawCircle(color, 2f, Offset(12f, 12f))
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, active: Boolean = false, onClick: (() -> Unit)? = null, color: Color = Teal) {
    val fill by
        androidx.compose.animation.animateColorAsState(
            if (active) Ink else Color.White.copy(alpha = .8f),
            tween(160),
            label = "tag-fill",
        )
    Box(
        Modifier.clip(SmallPanel)
            .background(fill)
            .then(
                if (onClick != null)
                    Modifier.heightIn(min = 48.dp)
                        .semantics { selected = active }
                        .clickable(onClick = onClick)
                else Modifier
            )
            .border(1.dp, if (active) Ink else Line, SmallPanel)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (active) Box(Modifier.size(3.dp, 12.dp).background(color))
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else Muted,
            )
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEach { Tag(it, it == selected, { onSelect(it) }) }
    }
}

@Composable
fun StatusTag(state: Care) {
    val c =
        when (state) {
            Care.READY -> DeepTeal
            Care.DIRTY -> Pink
            Care.WASHING -> Color(0xFF42658E)
        }
    Row(
        Modifier.background(Color.White.copy(alpha = .94f), SmallPanel)
            .border(1.dp, c.copy(alpha = .18f), SmallPanel)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(4.dp).background(c))
        Text(state.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
fun EmptyPanel(title: String, subtitle: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth()
            .clip(Panel)
            .background(Color.White.copy(alpha = .65f))
            .registrationMarks()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(66.dp).background(Ink, Panel), contentAlignment = Alignment.Center) {
            Glyph("hanger", Modifier.size(32.dp), Teal)
        }
        Micro("A LITTLE SPACE FOR SOMETHING NEW", DeepTeal)
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            fontSize = 13.sp,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) ActionButton(action, onAction)
    }
}

@Composable
fun Hero(modifier: Modifier = Modifier, large: Boolean = false) {
    EditorialBanner(
        "穿搭有序\n每天有新意",
        "整理衣物，发现更多搭配。",
        "THE DAILY EDIT",
        R.drawable.collection_stage,
        modifier,
        large,
    )
}

@Composable
fun GarmentArt(g: Garment, modifier: Modifier = Modifier) {
    val image by
        produceState<ImageBitmap?>(null, g.image) {
            value =
                if (g.image.isBlank()) null
                else
                    withContext(Dispatchers.IO) {
                        runCatching {
                                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(g.image, o)
                                var s = 1
                                while (maxOf(o.outWidth, o.outHeight) / s > 600) s *= 2
                                BitmapFactory.decodeFile(
                                        g.image,
                                        BitmapFactory.Options().apply { inSampleSize = s },
                                    )
                                    ?.asImageBitmap()
                            }
                            .getOrNull()
                    }
        }
    if (image != null) Image(image!!, g.name, modifier, contentScale = ContentScale.Crop)
    else
        Canvas(modifier.semantics { contentDescription = g.name }) {
            val hue = Color(PaletteHex[PaletteNames.indexOf(g.color).coerceAtLeast(0)])
            val light = Color(0xFFE7EBE7)
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFFF7F8F5), light),
                    Offset.Zero,
                    Offset(size.width, size.height),
                )
            )
            val grid = 30.dp.toPx()
            var x = grid
            while (x < size.width) {
                drawLine(Ink.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += grid
            }
            var y = grid
            while (y < size.height) {
                drawLine(Ink.copy(alpha = .035f), Offset(0f, y), Offset(size.width, y), 1f)
                y += grid
            }
            val unit = minOf(size.width, size.height) / 120f
            withTransform({
                translate((size.width - 120 * unit) / 2, (size.height - 120 * unit) / 2)
                scale(unit, unit, Offset.Zero)
            }) {
                drawOval(Ink.copy(alpha = .07f), Offset(26f, 100f), Size(72f, 7f))
                val path = Path()
                val style = g.style.ifBlank { Silhouettes[g.category]?.first() ?: "衬衫" }
                when (style) {
                    "长裤" -> {
                        path.moveTo(36f, 18f)
                        path.lineTo(83f, 18f)
                        path.lineTo(89f, 103f)
                        path.lineTo(65f, 103f)
                        path.lineTo(58f, 52f)
                        path.lineTo(52f, 103f)
                        path.lineTo(28f, 103f)
                        path.close()
                    }
                    "短裤" -> {
                        path.moveTo(34f, 30f)
                        path.lineTo(85f, 30f)
                        path.lineTo(91f, 87f)
                        path.lineTo(65f, 87f)
                        path.lineTo(59f, 61f)
                        path.lineTo(53f, 87f)
                        path.lineTo(27f, 87f)
                        path.close()
                    }
                    "半裙" -> {
                        path.moveTo(41f, 22f)
                        path.lineTo(79f, 22f)
                        path.lineTo(99f, 100f)
                        path.quadraticTo(60f, 111f, 21f, 100f)
                        path.close()
                    }
                    "连衣裙" -> {
                        path.moveTo(46f, 15f)
                        path.quadraticTo(60f, 26f, 74f, 15f)
                        path.lineTo(81f, 26f)
                        path.lineTo(72f, 53f)
                        path.lineTo(96f, 104f)
                        path.quadraticTo(60f, 112f, 24f, 104f)
                        path.lineTo(48f, 53f)
                        path.lineTo(39f, 26f)
                        path.close()
                    }
                    "运动鞋" -> {
                        path.moveTo(23f, 52f)
                        path.lineTo(45f, 58f)
                        path.lineTo(64f, 47f)
                        path.lineTo(74f, 69f)
                        path.cubicTo(88f, 72f, 103f, 77f, 103f, 89f)
                        path.lineTo(18f, 89f)
                        path.lineTo(17f, 75f)
                        path.close()
                    }
                    "低跟鞋" -> {
                        path.moveTo(20f, 50f)
                        path.quadraticTo(42f, 70f, 58f, 66f)
                        path.lineTo(73f, 54f)
                        path.lineTo(88f, 72f)
                        path.quadraticTo(110f, 77f, 100f, 85f)
                        path.lineTo(51f, 85f)
                        path.lineTo(29f, 68f)
                        path.lineTo(29f, 94f)
                        path.lineTo(21f, 94f)
                        path.close()
                    }
                    "短靴" -> {
                        path.moveTo(28f, 25f)
                        path.lineTo(64f, 25f)
                        path.lineTo(64f, 65f)
                        path.quadraticTo(110f, 66f, 104f, 90f)
                        path.lineTo(23f, 90f)
                        path.close()
                    }
                    "手提包" -> {
                        path.moveTo(28f, 41f)
                        path.lineTo(91f, 41f)
                        path.lineTo(99f, 100f)
                        path.lineTo(21f, 100f)
                        path.close()
                        drawRoundRect(
                            hue,
                            Offset(42f, 19f),
                            Size(36f, 36f),
                            androidx.compose.ui.geometry.CornerRadius(12f),
                            style = Stroke(5f),
                        )
                    }
                    "双肩包" -> {
                        path.moveTo(32f, 40f)
                        path.cubicTo(32f, 12f, 87f, 12f, 87f, 40f)
                        path.lineTo(92f, 99f)
                        path.lineTo(28f, 99f)
                        path.close()
                        drawRoundRect(
                            hue,
                            Offset(48f, 12f),
                            Size(24f, 18f),
                            androidx.compose.ui.geometry.CornerRadius(7f),
                            style = Stroke(4f),
                        )
                    }
                    "围巾" -> {
                        path.fillType = PathFillType.EvenOdd
                        path.moveTo(37f, 18f)
                        path.cubicTo(48f, 11f, 75f, 11f, 84f, 22f)
                        path.quadraticTo(95f, 37f, 78f, 49f)
                        path.lineTo(86f, 99f)
                        path.lineTo(65f, 106f)
                        path.lineTo(54f, 48f)
                        path.lineTo(48f, 89f)
                        path.lineTo(28f, 85f)
                        path.lineTo(37f, 44f)
                        path.quadraticTo(24f, 29f, 37f, 18f)
                        path.close()
                        path.addOval(Rect(43f, 22f, 77f, 38f))
                    }
                    "西装" -> {
                        path.moveTo(44f, 17f)
                        path.lineTo(30f, 23f)
                        path.lineTo(13f, 83f)
                        path.lineTo(27f, 88f)
                        path.lineTo(39f, 49f)
                        path.lineTo(35f, 102f)
                        path.lineTo(57f, 104f)
                        path.lineTo(61f, 96f)
                        path.lineTo(67f, 104f)
                        path.lineTo(86f, 102f)
                        path.lineTo(81f, 49f)
                        path.lineTo(93f, 88f)
                        path.lineTo(107f, 83f)
                        path.lineTo(91f, 24f)
                        path.lineTo(75f, 17f)
                        path.close()
                    }
                    "帽子" -> {
                        path.moveTo(28f, 69f)
                        path.cubicTo(25f, 12f, 92f, 12f, 92f, 69f)
                        path.lineTo(104f, 85f)
                        path.quadraticTo(60f, 101f, 16f, 85f)
                        path.close()
                    }
                    "T恤" -> {
                        path.moveTo(43f, 23f)
                        path.quadraticTo(60f, 37f, 77f, 23f)
                        path.lineTo(91f, 31f)
                        path.lineTo(106f, 48f)
                        path.lineTo(89f, 63f)
                        path.lineTo(81f, 55f)
                        path.lineTo(83f, 100f)
                        path.lineTo(37f, 100f)
                        path.lineTo(39f, 55f)
                        path.lineTo(31f, 63f)
                        path.lineTo(14f, 48f)
                        path.lineTo(29f, 31f)
                        path.close()
                    }
                    "风衣" -> {
                        path.moveTo(43f, 14f)
                        path.lineTo(76f, 14f)
                        path.lineTo(90f, 26f)
                        path.lineTo(109f, 70f)
                        path.lineTo(94f, 76f)
                        path.lineTo(79f, 47f)
                        path.lineTo(88f, 109f)
                        path.lineTo(31f, 109f)
                        path.lineTo(40f, 47f)
                        path.lineTo(26f, 76f)
                        path.lineTo(11f, 70f)
                        path.lineTo(29f, 26f)
                        path.close()
                    }
                    else -> {
                        path.moveTo(44f, 20f)
                        path.quadraticTo(60f, 30f, 76f, 20f)
                        path.lineTo(89f, 26f)
                        path.lineTo(109f, 62f)
                        path.lineTo(91f, 73f)
                        path.lineTo(82f, 54f)
                        path.lineTo(84f, 102f)
                        path.quadraticTo(60f, 107f, 36f, 102f)
                        path.lineTo(38f, 54f)
                        path.lineTo(29f, 73f)
                        path.lineTo(11f, 62f)
                        path.lineTo(31f, 26f)
                        path.close()
                    }
                }
                drawPath(
                    path,
                    Brush.linearGradient(
                        listOf(hue.copy(alpha = .8f), hue, hue.copy(alpha = .9f)),
                        Offset(20f, 20f),
                        Offset(98f, 110f),
                    ),
                )
                drawPath(path, Ink.copy(alpha = .1f), style = Stroke(.7f))
                if (style in listOf("衬衫", "风衣")) {
                    drawLine(Ink.copy(alpha = .18f), Offset(60f, 32f), Offset(60f, 100f), 1f)
                    drawPath(
                        Path().apply {
                            moveTo(44f, 20f)
                            lineTo(50f, 37f)
                            lineTo(60f, 29f)
                            lineTo(70f, 37f)
                            lineTo(76f, 20f)
                        },
                        Ink.copy(alpha = .2f),
                        style = Stroke(1f),
                    )
                    (45..92 step 12).forEach {
                        drawCircle(Ink.copy(alpha = .3f), 1f, Offset(63f, it.toFloat()))
                    }
                    drawLine(Ink.copy(alpha = .12f), Offset(68f, 46f), Offset(78f, 46f), 1f)
                }
                if (style == "西装") {
                    drawPath(
                        Path().apply {
                            moveTo(47f, 18f)
                            lineTo(60f, 54f)
                            lineTo(72f, 18f)
                            close()
                        },
                        Color(0xFFEDEBE3),
                    )
                    drawPath(
                        Path().apply {
                            moveTo(44f, 18f)
                            lineTo(38f, 33f)
                            lineTo(48f, 33f)
                            lineTo(44f, 43f)
                            lineTo(61f, 61f)
                            moveTo(75f, 18f)
                            lineTo(81f, 33f)
                            lineTo(71f, 33f)
                            lineTo(75f, 43f)
                            lineTo(61f, 61f)
                            lineTo(61f, 96f)
                        },
                        Ink.copy(alpha = .3f),
                        style = Stroke(1.2f),
                    )
                    for (y in listOf(67f, 80f)) drawCircle(
                        Ink.copy(alpha = .45f),
                        1.5f,
                        Offset(58f, y),
                    )
                    drawLine(Ink.copy(alpha = .25f), Offset(40f, 74f), Offset(52f, 76f), 1.5f)
                    drawLine(Ink.copy(alpha = .25f), Offset(69f, 76f), Offset(81f, 74f), 1.5f)
                    drawLine(Ink.copy(alpha = .25f), Offset(69f, 48f), Offset(79f, 47f), 1.5f)
                }
                if (style == "围巾") {
                    drawPath(
                        Path().apply {
                            moveTo(35f, 37f)
                            quadraticTo(61f, 57f, 84f, 30f)
                        },
                        Ink.copy(alpha = .18f),
                        style = Stroke(1.3f),
                    )
                    for (i in 0..4) {
                        drawLine(
                            hue,
                            Offset(29f + i * 4f, 85f + i * .8f),
                            Offset(28f + i * 4f, 92f + i * .8f),
                            1.8f,
                        )
                        drawLine(
                            hue,
                            Offset(66f + i * 4f, 106f - i * 1.3f),
                            Offset(67f + i * 4f, 112f - i * 1.3f),
                            1.8f,
                        )
                    }
                    drawLine(Ink.copy(alpha = .1f), Offset(61f, 51f), Offset(70f, 99f), 1f)
                    drawLine(Ink.copy(alpha = .1f), Offset(67f, 50f), Offset(77f, 98f), 1f)
                }
                if (style == "夹克") {
                    drawLine(Ink.copy(alpha = .28f), Offset(60f, 29f), Offset(60f, 100f), 1.5f)
                    drawRect(Ink.copy(alpha = .35f), Offset(58f, 39f), Size(4f, 6f))
                    drawLine(Ink.copy(alpha = .2f), Offset(37f, 98f), Offset(84f, 98f), 3f)
                    drawLine(Ink.copy(alpha = .2f), Offset(42f, 66f), Offset(48f, 79f), 1.4f)
                    drawLine(Ink.copy(alpha = .2f), Offset(78f, 66f), Offset(72f, 79f), 1.4f)
                }
                if (style == "半裙") {
                    for (i in 0..5) drawLine(
                        Ink.copy(alpha = .14f),
                        Offset(43f + i * 7f, 29f),
                        Offset(29f + i * 12f, 100f),
                        1f,
                    )
                    drawLine(Ink.copy(alpha = .2f), Offset(40f, 28f), Offset(80f, 28f), 2f)
                }
                if (style == "连衣裙" || style == "风衣") {
                    drawLine(Ink.copy(alpha = .25f), Offset(47f, 54f), Offset(74f, 54f), 3f)
                    drawLine(Ink.copy(alpha = .12f), Offset(56f, 57f), Offset(45f, 102f), 1f)
                    drawLine(Ink.copy(alpha = .12f), Offset(64f, 57f), Offset(76f, 102f), 1f)
                }
                if (style == "针织") {
                    for (i in 0..9) drawLine(
                        Ink.copy(alpha = .08f),
                        Offset(42f + i * 4, 36f),
                        Offset(42f + i * 4, 98f),
                        .8f,
                    )
                    drawLine(Ink.copy(alpha = .2f), Offset(37f, 97f), Offset(84f, 97f), 3f)
                }
                if (style == "卫衣") {
                    drawPath(
                        Path().apply {
                            moveTo(44f, 22f)
                            cubicTo(43f, 3f, 78f, 3f, 76f, 22f)
                            lineTo(62f, 38f)
                            close()
                        },
                        hue,
                    )
                    drawLine(Ink.copy(alpha = .3f), Offset(57f, 32f), Offset(54f, 55f), 1.4f)
                    drawLine(Ink.copy(alpha = .3f), Offset(64f, 32f), Offset(67f, 55f), 1.4f)
                    drawRoundRect(
                        Ink.copy(alpha = .08f),
                        Offset(44f, 74f),
                        Size(32f, 18f),
                        androidx.compose.ui.geometry.CornerRadius(4f),
                    )
                }
                if (style == "双肩包")
                    drawRoundRect(
                        Ink.copy(alpha = .16f),
                        Offset(38f, 65f),
                        Size(42f, 26f),
                        androidx.compose.ui.geometry.CornerRadius(5f),
                        style = Stroke(1.5f),
                    )
                if (style in listOf("长裤", "短裤")) {
                    drawLine(Ink.copy(alpha = .2f), Offset(38f, 26f), Offset(83f, 26f), 1f)
                    drawLine(Ink.copy(alpha = .15f), Offset(59f, 27f), Offset(59f, 47f), 1f)
                }
                if (style == "运动鞋") {
                    drawLine(Color.White, Offset(20f, 86f), Offset(100f, 86f), 5f)
                    repeat(4) {
                        drawLine(
                            Color.White,
                            Offset(51f + it * 5, 63f + it * 3),
                            Offset(63f + it * 4, 60f + it * 3),
                            2f,
                        )
                    }
                }
            }
        }
}

@Composable
fun GarmentCard(g: Garment, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(Panel)
            .background(Color.White)
            .border(1.dp, Line.copy(alpha = .7f), Panel)
            .clickable(onClick = onClick)
    ) {
        Box {
            GarmentArt(g, Modifier.fillMaxWidth().aspectRatio(1.12f))
            Box(Modifier.matchParentSize().registrationMarks())
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { StatusTag(g.care) }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Micro("NO.", Muted)
                Spacer(Modifier.width(3.dp))
                Numeral(
                    g.id.toString().padStart(3, '0'),
                    size = 24.sp,
                    color = Ink.copy(alpha = .48f),
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                g.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${g.category} · ${g.season}",
                    Modifier.weight(1f),
                    fontSize = 10.sp,
                    color = Muted,
                )
                Text(
                    "${count}次",
                    fontSize = 11.sp,
                    fontFamily = TechnicalFont,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepTeal,
                )
            }
        }
    }
}
