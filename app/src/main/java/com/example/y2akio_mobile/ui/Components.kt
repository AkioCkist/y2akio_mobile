package com.example.y2akio_mobile.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.y2akio_mobile.ui.theme.OneUi
import kotlinx.coroutines.launch

// ─── Collapsing "viewing area" app bar ───────────────────────────────────────

/**
 * One UI screen frame: a tall header (the "viewing area", ~⅓ of the screen) holding the big title,
 * which collapses into a 56dp toolbar as the list scrolls and snaps open/closed after a fling. The
 * list below is the "interaction area".
 */
@Composable
fun OneUiScreen(
    title: String,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    expandedHeader: @Composable ColumnScope.() -> Unit = {
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
    },
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val collapsed = OneUi.CollapsedBarHeight + statusBar
        val expanded = (maxHeight * 0.34f).coerceIn(collapsed + 120.dp, 340.dp)
        val collapsedPx = with(density) { collapsed.toPx() }
        val expandedPx = with(density) { expanded.toPx() }

        var heightPx by remember(expandedPx) { mutableFloatStateOf(expandedPx) }
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        val connection = remember(collapsedPx, expandedPx) {
            object : NestedScrollConnection {
                // Scrolling up collapses the header before the list moves.
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0) return Offset.Zero
                    val next = (heightPx + available.y).coerceAtLeast(collapsedPx)
                    val used = next - heightPx
                    heightPx = next
                    return Offset(0f, used)
                }

                // Scrolling down expands it only once the list is back at its top.
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y <= 0) return Offset.Zero
                    val next = (heightPx + available.y).coerceAtMost(expandedPx)
                    val used = next - heightPx
                    heightPx = next
                    return Offset(0f, used)
                }

                // Never rest half-open: snap to the nearer state.
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (heightPx > collapsedPx && heightPx < expandedPx) {
                        val target = if (heightPx - collapsedPx > (expandedPx - collapsedPx) / 2) expandedPx else collapsedPx
                        animate(heightPx, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> heightPx = v }
                    }
                    return super.onPostFling(consumed, available)
                }
            }
        }

        val progress = ((heightPx - collapsedPx) / (expandedPx - collapsedPx)).coerceIn(0f, 1f)
        val headerHeight = with(density) { heightPx.toDp() }

        Column(Modifier.fillMaxSize().nestedScroll(connection)) {
            Box(Modifier.fillMaxWidth().height(headerHeight)) {
                // Expanded title, vertically centred in the viewing area, fades as it collapses.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(top = statusBar, start = 28.dp, end = 28.dp)
                        .alpha(((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = expandedHeader,
                )
                // Collapsed toolbar.
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(OneUi.CollapsedBarHeight)
                        .padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f).alpha((1f - progress * 2.5f).coerceIn(0f, 1f)),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    actions()
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = OneUi.ScreenMargin, end = OneUi.ScreenMargin, bottom = bottomPadding + 28.dp),
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
        // Tap the collapsed title to jump back to the top.
        if (progress == 0f) {
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth(0.6f)
                    .height(OneUi.CollapsedBarHeight)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch {
                            listState.animateScrollToItem(0)
                            animate(heightPx, expandedPx) { v, _ -> heightPx = v }
                        }
                    },
            )
        }
    }
}

// ─── Grouped cards ───────────────────────────────────────────────────────────

/** Small accent-coloured category label above a card group. */
@Composable
fun SubHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = OneUi.SubHeaderStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = OneUi.CardPadding, end = OneUi.CardPadding, top = 22.dp, bottom = 8.dp),
    )
}

/** One UI rounded card on the grey canvas. */
@Composable
fun RoundedGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUi.CardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Shape for one row of a card that is split across several LazyColumn items: only the first row
 * rounds its top, only the last rounds its bottom.
 */
fun groupShape(index: Int, count: Int): Shape {
    val r = OneUi.CardRadius
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == count - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}

@Composable
fun Modifier.groupItem(index: Int, count: Int): Modifier = this
    .fillMaxWidth()
    .clip(groupShape(index, count))
    .background(MaterialTheme.colorScheme.surfaceContainerLow)

/** Inset divider between rows of a card (aligned with the row text). */
@Composable
fun RowDivider(startInset: Dp = OneUi.CardPadding) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset, end = OneUi.CardPadding),
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ─── Rows ────────────────────────────────────────────────────────────────────

/** Title + optional summary row; the summary shows the current value in the accent colour. */
@Composable
fun PrefRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryAccent: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = OneUi.RowMinHeight)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = OneUi.CardPadding, vertical = 12.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summaryAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun oneUiSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = Color.Transparent,
)

/** Whole-row toggle (One UI uses switches, not checkboxes, for on/off settings). */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null, enabled: Boolean = true) {
    PrefRow(title, summary = summary, enabled = enabled, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = oneUiSwitchColors(), thumbContent = null)
    }
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(start = OneUi.CardPadding - 12.dp, end = OneUi.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Spinner row: title with the chosen value as accent summary; tapping opens a rounded popup
 * anchored to the row's end (One UI's popup menu).
 */
@Composable
fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: selected
    Box {
        PrefRow(
            title,
            summary = if (hint != null) "$current\n$hint" else current,
            summaryAccent = true,
            enabled = enabled,
            onClick = { expanded = true },
        )
        Box(Modifier.align(Alignment.BottomEnd).padding(end = OneUi.CardPadding)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(22.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) {
                options.forEach { (value, text) ->
                    val isSel = value == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = { onSelect(value); expanded = false },
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ─── Buttons / segmented control ─────────────────────────────────────────────

/** One UI primary action: filled, fully rounded. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tonal: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        elevation = null,
        colors = if (tonal) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) else ButtonDefaults.buttonColors(),
    ) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Accent text button used in card headers ("Select all", "Clear"). */
@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One UI tab-style segmented control: grey track, raised white pill on the selected segment. */
@Composable
fun SegmentedPill(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(stiffness = Spring.StiffnessMediumLow), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxSize()
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == selectedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
