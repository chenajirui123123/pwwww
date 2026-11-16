package com.wardrobe.atelier.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.wardrobe.atelier.domain.Garment

@Composable
fun RevealPanel(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible,
        enter =
            expandVertically(tween(240, easing = FastOutSlowInEasing), Alignment.Top) +
                fadeIn(tween(160, 30)),
        exit =
            shrinkVertically(tween(180, easing = FastOutSlowInEasing), Alignment.Top) +
                fadeOut(tween(100)),
        content = content,
    )
}

/** Each category is a stable slot: locked clothes remain still when the others slide in. */
@Composable
fun OutfitBoard(
    garments: List<Garment>,
    modifier: Modifier = Modifier,
    locked: Set<Long> = emptySet(),
    onToggleLock: ((Long) -> Unit)? = null,
) {
    val bodies = garments.filter { it.category in setOf("上装", "下装", "连衣裙") }
    val others = garments.filterNot { it in bodies }
    // A preview must stay legible even for manually composed collections with many pieces.
    // Full item lists remain available below the board in the editor and outfit detail.
    val primary = bodies.ifEmpty { garments.take(1) }.take(2)
    val extras = (if (bodies.isEmpty()) garments.drop(1) else others).take(4)
    val overflow = garments.size - primary.size - extras.size
    Box(modifier) {
        Row(
            Modifier.fillMaxSize().background(Paper).padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (primary.isEmpty())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Glyph("outfit", Modifier.size(42.dp), Muted)
                }
            else {
                Column(
                    Modifier.weight(if (extras.size > 2) 1f else 1.2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    primary.forEachIndexed { index, g ->
                        key(g.category, index) {
                            BoardTile(
                                g,
                                Modifier.fillMaxWidth().weight(1f),
                                g.id in locked,
                                onToggleLock,
                            )
                        }
                    }
                }
                if (extras.isNotEmpty())
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        extras.chunked(if (extras.size > 2) 2 else 1).forEach { row ->
                            Row(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                row.forEachIndexed { index, g ->
                                    key(g.category, index) {
                                        BoardTile(
                                            g,
                                            Modifier.weight(1f).fillMaxHeight(),
                                            g.id in locked,
                                            onToggleLock,
                                        )
                                    }
                                }
                                if (extras.size > 2 && row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
            }
        }
        if (overflow > 0)
            Text(localized("+$overflow 件 · 见单品列表"),
                Modifier.align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Ink, SmallPanel)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
    }
}

@Composable
private fun BoardTile(
    g: Garment,
    modifier: Modifier,
    locked: Boolean,
    onToggleLock: ((Long) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(110), label = "board-press")
    val border by animateColorAsState(if (locked) Pink else Line, tween(160), label = "board-lock")
    val width by animateDpAsState(if (locked) 2.dp else 1.dp, tween(160), label = "board-outline")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(SmallPanel)
            .background(Color.White)
            .border(width, border, SmallPanel)
            .then(
                if (onToggleLock != null)
                    Modifier.semantics {
                            selected = locked
                            contentDescription = localized(if (locked) "解锁" else "锁定") + " " + g.name
                        }
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                        ) {
                            onToggleLock(g.id)
                        }
                else Modifier
            )
    ) {
        AnimatedContent(
            targetState = g,
            contentKey = { it.id to it.image },
            transitionSpec = {
                (slideInHorizontally(tween(280, 35, FastOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(180, 35))) togetherWith
                    (slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { -it / 5 } +
                        fadeOut(tween(120)))
            },
            label = "swap-one-piece",
        ) { garment ->
            GarmentArt(garment, Modifier.fillMaxSize())
        }
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.White.copy(alpha = .91f), SmallPanel)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(localized(g.category), fontSize = 10.sp, color = Ink, fontWeight = FontWeight.Bold)
        }
        if (onToggleLock != null)
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(if (locked) Pink else Color.White.copy(alpha = .94f), SmallPanel)
                    .padding(5.dp)
            ) {
                Glyph(
                    if (locked) "lock" else "plus",
                    Modifier.size(13.dp),
                    if (locked) Color.White else DeepTeal,
                )
            }
    }
}
