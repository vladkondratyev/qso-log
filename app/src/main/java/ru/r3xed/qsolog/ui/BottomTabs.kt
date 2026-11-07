package ru.r3xed.qsolog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.tr

/** The screens with the bottom bar; the card, the settings and the rest open over it without one. */
val BOTTOM_TABS = setOf(Screen.Log, Screen.DxCluster, Screen.Map, Screen.Dashboard, Screen.Awards, Screen.Propagation)

/** Журнал · Кластер · Карта · Статистика (the awards belong to the statistics tab). */
@Composable
fun BottomTabs(vm: AppViewModel) {
    val tabs: List<Triple<Screen, String, ImageVector>> = listOf(
        Triple(Screen.Log, tr("Журнал"), Icons.AutoMirrored.Filled.List),
        Triple(Screen.DxCluster, tr("Кластер"), Icons.Filled.CellTower),
        Triple(Screen.Map, tr("Карта"), Icons.Filled.Map),
        Triple(Screen.Dashboard, tr("Статистика"), Icons.Filled.BarChart),
    )
    // Compact: 56 dp instead of Material's 80, small labels, the chosen tab in a pill.
    val x = LocalExtra.current
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
        HorizontalDivider(color = x.line)
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (screen, label, icon) ->
                val on = vm.screen == screen || (screen == Screen.Dashboard && (vm.screen == Screen.Awards || vm.screen == Screen.Propagation))
                val tint = if (on) MaterialTheme.colorScheme.onSecondaryContainer else x.muted
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected = on, role = Role.Tab) {
                        when (screen) {
                            Screen.DxCluster -> vm.openDxCluster()
                            Screen.Map -> vm.openMap()
                            Screen.Dashboard -> vm.openDashboard()
                            else -> vm.screen = Screen.Log
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    ) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }
                    Text(label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else null, color = tint, maxLines = 1)
                }
            }
        }
    }
}
