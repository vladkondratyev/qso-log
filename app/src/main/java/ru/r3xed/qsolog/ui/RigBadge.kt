package ru.r3xed.qsolog.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import ru.r3xed.qsolog.SettingsPage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.AppViewModel
import ru.r3xed.qsolog.cat.CatStatus
import ru.r3xed.qsolog.cat.short
import ru.r3xed.qsolog.tr

/**
 * The transceiver in the QSO card, when CAT is on: a green dot and "TRX 14 074.000 · USB · S7" while it answers,
 * a grey or red one with the reason when it does not. Without CAT, the dial WSJT-X or JTDX reports ("JTDX 14 074.000 ·
 * FT8"). A tap opens the transceiver panel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RigBadge(vm: AppViewModel, modifier: Modifier = Modifier) {
    val rig = vm.rig
    if (!vm.catConfig.enabled && rig == null) return
    val x = LocalExtra.current
    val st = vm.catStatus
    val bad = vm.catConfig.enabled && rig == null && (st is CatStatus.Failed || (st is CatStatus.Connected && st.silent))
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(x.field)
            // A long press goes straight to the CAT settings (the equipment page), back to here after.
            .combinedClickable(
                onClickLabel = tr("Пульт трансивера"),
                onLongClickLabel = tr("Настройки CAT"),
                onLongClick = { vm.openSettings(from = vm.screen, page = SettingsPage.HARDWARE) },
            ) { vm.showRigPanel = true }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(9.dp).clip(CircleShape).background(if (rig != null) x.ok else if (bad) MaterialTheme.colorScheme.error else x.muted))
        Spacer(Modifier.width(7.dp))
        Text(if (rig != null) vm.rigSource + " " else "TRX ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = x.muted)
        Text(
            rig?.short()?.ifBlank { null } ?: when {
                bad -> tr("нет связи")
                else -> tr("подключаюсь…")
            },
            fontFamily = if (rig != null) Mono else null, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
