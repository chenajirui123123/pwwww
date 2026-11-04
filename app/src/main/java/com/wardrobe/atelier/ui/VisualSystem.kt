package com.wardrobe.atelier.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.R

// Bundled SIL-OFL fonts: deterministic offline rendering on every supported phone.
val TechnicalFont =
    FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
val NumeralFont =
    FontFamily(
        Font(R.font.barlow_condensed_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic)
    )
val DeepTeal = Color(0xFF087B73)

@Composable
fun UtilizationRing(value: Float, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by
        animateFloatAsState(
            if (entered) value else 0f,
            tween(650, easing = FastOutSlowInEasing),
            label = "utilization",
        )
    Canvas(modifier.progressSemantics(value.coerceIn(0f, 1f))) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2 + 3.dp.toPx()
        val area = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(
            Color(0xFF414A4C),
            -90f,
            360f,
            false,
            Offset(inset, inset),
            area,
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )
        drawArc(
            Teal,
            -90f,
            360f * progress.coerceIn(0f, 1f),
            false,
            Offset(inset, inset),
            area,
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )
        // Four exterior registration marks do not encode data.
        val c = Color.White.copy(alpha = .35f)
        drawLine(c, Offset(size.width / 2, 0f), Offset(size.width / 2, 2.dp.toPx()), 1.dp.toPx())
        drawLine(
            c,
            Offset(size.width / 2, size.height),
            Offset(size.width / 2, size.height - 2.dp.toPx()),
            1.dp.toPx(),
        )
        drawLine(c, Offset(0f, size.height / 2), Offset(2.dp.toPx(), size.height / 2), 1.dp.toPx())
        drawLine(
            c,
            Offset(size.width, size.height / 2),
            Offset(size.width - 2.dp.toPx(), size.height / 2),
            1.dp.toPx(),
        )
    }
}

@Composable
fun RatioBar(value: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.progressSemantics(value.coerceIn(0f, 1f))) {
        drawRect(Line.copy(alpha = .65f))
        drawRect(Teal, size = Size(size.width * value.coerceIn(0f, 1f), size.height))
        repeat(3) { i ->
            val x = size.width * (i + 1) / 4
            drawLine(
                Color.White.copy(alpha = .6f),
                Offset(x, 0f),
                Offset(x, size.height),
                1.dp.toPx(),
            )
        }
    }
}

@Composable
fun Numeral(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 32.sp,
    color: Color = Ink,
) {
    Text(
        text,
        modifier,
        fontFamily = NumeralFont,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        fontSize = size,
        lineHeight = size,
        letterSpacing = (-.4).sp,
        color = color,
        maxLines = 1,
    )
}

/** Quiet registration marks tie the surfaces together without becoming controls. */
fun Modifier.registrationMarks(color: Color = Ink.copy(alpha = .16f)): Modifier = drawBehind {
    val inset = 8.dp.toPx()
    val arm = 8.dp.toPx()
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(inset, inset + arm), Offset(inset, inset), stroke)
    drawLine(color, Offset(inset, inset), Offset(inset + arm, inset), stroke)
    drawLine(
        color,
        Offset(size.width - inset - arm, size.height - inset),
        Offset(size.width - inset, size.height - inset),
        stroke,
    )
    drawLine(
        color,
        Offset(size.width - inset, size.height - inset),
        Offset(size.width - inset, size.height - inset - arm),
        stroke,
    )
}

@Composable
fun Slashes(modifier: Modifier = Modifier, color: Color = Teal) {
    Canvas(modifier.size(20.dp, 12.dp)) {
        repeat(3) { i ->
            val x = i * size.width / 3
            drawLine(color, Offset(x, size.height), Offset(x + size.width / 4, 0f), 2.dp.toPx())
        }
    }
}

@Composable
fun CodeLabel(code: String, modifier: Modifier = Modifier) {
    if (code.isBlank()) return
    val count = code.substringBefore(' ').takeIf { it.all(Char::isDigit) && it.isNotBlank() }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (count != null) {
            Numeral(count, size = 20.sp, color = DeepTeal)
            Micro(code.substringAfter(' '))
        } else Micro(code)
    }
}

@Composable
fun ModuleHeading(title: String, code: String, action: String? = null, onAction: () -> Unit = {}) {
    val number = code.substringBefore(" / ")
    val label = code.substringAfter(" / ")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Ink, SmallPanel).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Numeral(number, size = 24.sp, color = Teal)
            }
            Spacer(Modifier.width(8.dp))
            Micro(label, Ink)
            Spacer(Modifier.width(12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Line)
            Spacer(Modifier.width(10.dp))
            Slashes(color = Ink.copy(alpha = .25f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                Modifier.weight(1f),
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-.7).sp,
            )
            if (action != null) ActionButton(action, onAction, icon = "plus")
        }
    }
}

@Composable
fun AtelierNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("衣橱", "灵感", "我的")
    val icons = listOf("hanger", "outfit", "user")
    Column(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding()) {
        HorizontalDivider(color = Teal.copy(alpha = .45f))
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            labels.forEachIndexed { i, label ->
                val active = selected == i
                val tint by
                    animateColorAsState(
                        if (active) Teal else Color(0xFFB8C3C1),
                        tween(180),
                        label = "nav-tint",
                    )
                val fill by
                    animateColorAsState(
                        if (active) Color(0xFF344543) else Ink,
                        tween(180),
                        label = "nav-fill",
                    )
                Column(
                    Modifier.weight(1f)
                        .padding(horizontal = 3.dp)
                        .clip(SmallPanel)
                        .background(fill)
                        .selectable(active, role = Role.Tab, onClick = { onSelect(i) })
                        .heightIn(min = 64.dp)
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Glyph(icons[i], Modifier.size(23.dp), tint)
                    Text(
                        label,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        Modifier.size(18.dp, 2.dp)
                            .background(if (active) Teal else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun EditorialBanner(
    title: String,
    subtitle: String,
    code: String,
    image: Int,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (large) 272.dp else 154.dp)
            .clip(Panel)
            .background(Color(0xFFEEF1EE))
            .border(1.dp, Color.White, Panel)
    ) {
        Image(
            painterResource(image),
            null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFFEEF1EE),
                        .38f to Color(0xFFEEF1EE).copy(alpha = .9f),
                        .63f to Color(0xFFEEF1EE).copy(alpha = .05f),
                    )
                )
        )
        Column(
            Modifier.padding(18.dp).widthIn(max = if (large) 230.dp else 180.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp, 12.dp).background(Pink))
                Spacer(Modifier.width(6.dp))
                Micro(code, DeepTeal)
            }
            Text(
                title,
                fontSize = if (large) 31.sp else 23.sp,
                lineHeight = if (large) 39.sp else 30.sp,
                letterSpacing = (-.8).sp,
                fontWeight = FontWeight.Black,
                color = Ink,
            )
            Text(subtitle, fontSize = 11.sp, lineHeight = 17.sp, color = Muted)
            if (large) {
                Spacer(Modifier.height(16.dp))
                Micro("YOUR WARDROBE. YOUR RHYTHM.", Ink)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Slashes(color = Pink) }
    }
}

@Composable
fun CollectionMetrics(values: List<Pair<Int, String>>) {
    val stacked = values.any { it.first >= 100 } || LocalDensity.current.fontScale > 1.2f
    Row(
        Modifier.fillMaxWidth()
            .clip(Panel)
            .background(Ink)
            .registrationMarks(Color.White.copy(alpha = .18f))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { i, (value, label) ->
            if (i > 0)
                Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = .12f)))
            if (stacked)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Numeral(
                        value.toString().padStart(2, '0'),
                        size = if (value >= 10000) 22.sp else if (value >= 1000) 26.sp else 32.sp,
                        color = if (i == 1) Teal else Color.White,
                    )
                    Text(label.replace('\n', ' '), color = Color(0xFFC3CECB), fontSize = 10.sp)
                }
            else
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Numeral(
                        value.toString().padStart(2, '0'),
                        size = 32.sp,
                        color = if (i == 1) Teal else Color.White,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(label, color = Color(0xFFC3CECB), fontSize = 10.sp, lineHeight = 15.sp)
                }
        }
    }
}

@Composable
fun AtelierTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    shape: Shape = SmallPanel,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value,
        onValueChange,
        modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        shape = shape,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Ink),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White.copy(alpha = .75f),
                focusedBorderColor = DeepTeal,
                unfocusedBorderColor = Line,
                focusedLabelColor = DeepTeal,
                unfocusedLabelColor = Muted,
                cursorColor = Pink,
            ),
    )
}
