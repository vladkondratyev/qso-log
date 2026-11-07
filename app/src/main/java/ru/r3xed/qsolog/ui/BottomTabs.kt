package ru.r3xed.qsolog.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.Screen
import ru.r3xed.qsolog.tr

/** The screens with the bottom bar; the card, the settings and the rest open over it without one. */
val BOTTOM_TABS = setOf(Screen.Log, Screen.DxCluster, Screen.Map, Screen.Dashboard, Screen.Awards)

/** Журнал · Кластер · Карта · Статистика (the awards belong to the statistics tab). */
@Composable
fun BottomTabs(vm: AppViewModel) {
    val tabs: List<Triple<Screen, String, ImageVector>> = listOf(
        Triple(Screen.Log, tr("Журнал"), Icons.AutoMirrored.Filled.List),
        Triple(Screen.DxCluster, tr("Кластер"), Icons.Filled.CellTower),
        Triple(Screen.Map, tr("Карта"), Icons.Filled.Map),
        Triple(Screen.Dashboard, tr("Статистика"), Icons.Filled.BarChart),
    )
    NavigationBar {
        tabs.forEach { (screen, label, icon) ->
            val on = vm.screen == screen || (screen == Screen.Dashboard && vm.screen == Screen.Awards)
            NavigationBarItem(
                selected = on,
                onClick = {
                    when (screen) {
                        Screen.DxCluster -> vm.openDxCluster()
                        Screen.Map -> vm.openMap()
                        Screen.Dashboard -> vm.openDashboard()
                        else -> vm.screen = Screen.Log
                    }
                },
                icon = { Icon(icon, null) },
                label = { Text(label) },
            )
        }
    }
}
