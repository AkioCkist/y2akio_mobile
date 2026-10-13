package com.example.y2akio_mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

data class GlassTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Height of the floating bar; screens pad their lists by this plus [GlassTabBarBottomGap]. */
val GlassTabBarHeight = 64.dp
val GlassTabBarBottomGap = 12.dp

/**
 * Floating Telegram-style tab bar made of Liquid Glass (Kyant0/AndroidLiquidGlass): a capsule that
 * blurs, saturates and refracts the content scrolling under it, with a frosted pill sliding behind
 * the active tab. [backdrop] must be the layer the screen content is recorded into
 * (`Modifier.layerBackdrop`).
 */
@Composable
fun GlassTabBar(
    backdrop: Backdrop,
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val surfaceTint = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.45f) else Color(0xFFFAFAFA).copy(alpha = 0.55f)
    val pillColor = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)

    // Whole bar bounces slightly while a tab is pressed, like the iOS / Telegram glass bars.
    val sources = remember(tabs.size) { List(tabs.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState() }
    val anyPressed = pressed.any { it.value }
    val barScale by animateFloatAsState(if (anyPressed) 1.03f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "barScale")

    Box(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(if (tabs.size <= 2) 0.66f else 0.92f)
            .height(GlassTabBarHeight)
            .graphicsLayer { scaleX = barScale; scaleY = barScale }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(18.dp.toPx(), 26.dp.toPx())
                },
                onDrawSurface = { drawRect(surfaceTint) },
            )
            .padding(5.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                tabWidth * selectedIndex,
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "pill",
            )
            // Sliding selection pill.
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(pillColor),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                tabs.forEachIndexed { i, tab ->
                    val selected = i == selectedIndex
                    val tint by animateColorAsState(
                        if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        label = "tabTint",
                    )
                    val iconScale by animateFloatAsState(if (pressed[i].value) 0.86f else 1f, spring(dampingRatio = 0.45f), label = "iconScale")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(interactionSource = sources[i], indication = null) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = tint,
                            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        Text(
                            tab.label, color = tint, fontSize = 11.sp, lineHeight = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
