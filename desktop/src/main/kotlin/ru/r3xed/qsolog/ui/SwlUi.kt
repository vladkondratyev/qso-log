package ru.r3xed.qsolog.ui

import androidx.compose.runtime.Composable
import ru.r3xed.qsolog.tr

/** «Наблюдатель (SWL)» of the station in work: its contacts become listener's reports (see [ru.r3xed.qsolog.data.Swl]). */
@Composable
internal fun StationSwlSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(tr("Наблюдатель (SWL)"), on, onChange = onChange)
    if (on) Note(
        tr("Связи этой станции записываются как наблюдения: позывной — кого слышали, «Работал с» — с кем он работал (на месте «RST принят»), RST — ваш рапорт. В ADIF у них SWL = Y. В LoTW, eQSL, Club Log, QRZ.com и HAMLOG наблюдения не выгружаются, QSL-наклейки печатаются как SWL-рапорт.")
    )
}
