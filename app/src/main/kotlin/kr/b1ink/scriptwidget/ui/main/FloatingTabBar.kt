package kr.b1ink.scriptwidget.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import kr.b1ink.scriptwidget.R

/** Room tab screens leave at the bottom of their lists so the floating bar doesn't cover content. */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Height of the bar plus its gap above the gesture area. */
val FloatingTabBarSpace = 96.dp

private val PILL_WIDTH = 52.dp
private val PILL_HEIGHT = 30.dp
private val PILL_TOP = 9.dp
private val PillShape = RoundedCornerShape(50)

private data class TabSpec(val tab: MainTab, @StringRes val label: Int, val icon: ImageVector)

private fun outlineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private val TABS = listOf(
    TabSpec(MainTab.Scripts, R.string.main_tab_scripts, outlineIcon("code", "M8 7l-5 5 5 5", "M16 7l5 5-5 5")),
    TabSpec(
        MainTab.Gallery, R.string.main_tab_gallery,
        outlineIcon(
            "gallery",
            "M5 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M16 3h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M5 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
            "M16 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        ),
    ),
    TabSpec(MainTab.History, R.string.main_tab_history, outlineIcon("history", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 7v5l3 2")),
    TabSpec(MainTab.Help, R.string.main_tab_help, outlineIcon("help", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 19V5", "M8 7h8", "M8 11h6")),
    TabSpec(
        MainTab.Settings, R.string.main_tab_settings,
        outlineIcon(
            "settings",
            "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
            "M12 2v3", "M12 19v3", "M2 12h3", "M19 12h3",
            "M4.9 4.9l2.1 2.1", "M17 17l2.1 2.1", "M4.9 19.1L7 17", "M17 7l2.1-2.1",
        ),
    ),
)

/** The bar floating above the bottom edge: five tabs, the selected one in a capsule. */
@Composable
fun FloatingTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 6.dp)) {
            val tabWidth = maxWidth / TABS.size
            val index = TABS.indexOfFirst { it.tab == selected }.coerceAtLeast(0)
            // One capsule that slides to the selected tab, so two tabs never look selected at once.
            val indicatorX by animateDpAsState(
                targetValue = tabWidth * index + (tabWidth - PILL_WIDTH) / 2,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                label = "indicator",
            )
            Box(
                Modifier
                    .offset(x = indicatorX, y = PILL_TOP)
                    .size(PILL_WIDTH, PILL_HEIGHT)
                    .background(MaterialTheme.colorScheme.secondaryContainer, PillShape)
                    .testTag("tab-indicator"),
            )
            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.Top,
            ) {
                TABS.forEach { spec ->
                    val isSelected = spec.tab == selected
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("tab-${spec.tab.name}")
                            // The press ripple is drawn in the rounded capsule below, not over the whole cell.
                            .selectable(
                                selected = isSelected,
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(spec.tab) },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .padding(top = PILL_TOP)
                                .size(PILL_WIDTH, PILL_HEIGHT)
                                .clip(PillShape)
                                .indication(interaction, ripple()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                spec.icon, contentDescription = null, modifier = Modifier.size(22.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(spec.label),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
