/*
 * Layout, indicator motion and drag handling adapted from BitChord's FloatingBottomBar
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.frosted
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt

/** Les trois onglets racine, dans l'ordre visuel — l'ordre sert aussi au glissement. */
internal val NAV_TABS: List<Triple<NavKey, ImageVector, Int>> = listOf(
    Triple(HomeScreenKey, Icons.Rounded.Home, R.string.home),
    Triple(SearchScreenKey(), Icons.Rounded.Search, R.string.search),
    Triple(LibraryScreenKey, Icons.Rounded.LibraryMusic, R.string.library),
)

private val BAR_GUTTER = 19.dp
private val BAR_LIFT = 6.dp
private val PILL_INSET = 4.dp
private val TAB_GAP = 6.dp
private val TAB_VERTICAL_PADDING = 6.5.dp
private val TAB_ICON_SIZE = 28.dp

/** Le pied de la barre garde sa hauteur quand la navigation par gestes n'a aucun inset. */
private val GESTURE_BAR_FLOOR = 15.dp

/** Le ressort de l'indicateur et des icones : un seul depassement discret, puis arret. */
private val TabSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

/** Allongement de l'indicateur en plein trajet : perceptible sans paraitre etire. */
private const val STRETCH = 0.16f
private const val SQUASH = 0.5f

/**
 * The tab a drag has reached: [startIndex] plus the distance dragged, clamped to the bar.
 * [stepPx] is the distance between two tabs.
 */
internal fun draggedTabPosition(
    startIndex: Int,
    dragPx: Float,
    stepPx: Float,
    lastIndex: Int,
): Float = (startIndex + dragPx / stepPx).coerceIn(0f, lastIndex.toFloat())

/**
 * Barre flottante en pilule de verre depoli : le contenu defile dessous, et l'indicateur peut se
 * tirer d'un onglet a l'autre. Le mini-player garde son propre glissement (piste precedente et
 * suivante) : les deux zones ne se recouvrent pas.
 *
 * @param hazeState capture le contenu qui defile sous la barre.
 */
@Composable
fun BottomNavigationBar(
    currentTab: NavKey?,
    onTabSelected: (NavKey) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val selectedIndex = NAV_TABS.indexOfFirst { it.first == currentTab }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val gapPx = with(density) { TAB_GAP.toPx() }
    val tabCount = NAV_TABS.size

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var lastTickedTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
        lastTickedTab = selectedIndex
    }

    // Avec weight(1f) et un espacement fixe : largeur = (ligne - espaces) / n, pas = (ligne + espace) / n.
    val tabWidthPx = (rowSize.width - gapPx * (tabCount - 1)) / tabCount
    val tabStepPx = (rowSize.width + gapPx) / tabCount
    val indicatorTarget = selectedIndex.coerceAtLeast(0) * tabStepPx + dragOffset
    val indicatorOffset by animateFloatAsState(
        targetValue = indicatorTarget,
        animationSpec = TabSpring,
        label = "tabIndicatorOffset",
    )
    // Ne se deforme que tant qu'il lui reste du chemin : au repos, il retrouve sa forme tout seul.
    val lag = if (tabStepPx > 0f) {
        (abs(indicatorTarget - indicatorOffset) / tabStepPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Le geste vit aussi longtemps que la barre : il relit ces valeurs plutot que de les figer.
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentStepPx by rememberUpdatedState(tabStepPx)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    Box(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))
            )
            .padding(horizontal = BAR_GUTTER)
            .padding(bottom = BAR_LIFT)
            .fillMaxWidth()
            .clip(pillShape)
            .frosted(hazeState)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = pillShape,
            )
            .padding(PILL_INSET),
    ) {
        if (selectedIndex >= 0 && tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(with(density) { rowSize.height.toDp() })
                    .graphicsLayer {
                        translationX = indicatorOffset
                        scaleX = 1f + lag * STRETCH
                        scaleY = 1f - lag * STRETCH * SQUASH
                    }
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowSize = it }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    var dragStartIndex = 0
                    detectHorizontalDragGestures(
                        onDragStart = {
                            totalDrag = 0f
                            dragStartIndex = currentSelectedIndex
                        },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = {
                            val step = currentStepPx
                            if (step > 0f && dragStartIndex >= 0) {
                                val target = draggedTabPosition(
                                    dragStartIndex, totalDrag, step, NAV_TABS.lastIndex,
                                ).roundToInt()
                                if (target != currentSelectedIndex) {
                                    currentOnTabSelected(NAV_TABS[target].first)
                                }
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            val step = currentStepPx
                            if (step <= 0f || dragStartIndex < 0) {
                                return@detectHorizontalDragGestures
                            }
                            totalDrag += delta
                            val position = draggedTabPosition(
                                dragStartIndex, totalDrag, step, NAV_TABS.lastIndex,
                            )
                            dragOffset = (position - currentSelectedIndex) * step
                            if (position.roundToInt() != lastTickedTab) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                lastTickedTab = position.roundToInt()
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NAV_TABS.forEach { (key, icon, labelRes) ->
                BottomBarTab(
                    icon = icon,
                    label = stringResource(labelRes),
                    selected = currentTab == key,
                    onClick = { onTabSelected(key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = TabSpring,
        label = "tabScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .keyboardFocusOutline(RoundedCornerShape(percent = 50))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = TAB_VERTICAL_PADDING),
    ) {
        // Le libelle juste en dessous nomme deja l'onglet pour un lecteur d'ecran.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(TAB_ICON_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
