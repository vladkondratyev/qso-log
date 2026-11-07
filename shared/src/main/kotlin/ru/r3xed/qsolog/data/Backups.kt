package ru.r3xed.qsolog.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** "Автокопия журнала": how often the whole log is saved as an ADIF file next to the app's data. */
enum class BackupPeriod(val days: Int) {
    OFF(0), DAILY(1), WEEKLY(7);

    val title: String
        get() = when (this) {
            OFF -> ru.r3xed.qsolog.tr("Выключена")
            DAILY -> ru.r3xed.qsolog.tr("Каждый день")
            WEEKLY -> ru.r3xed.qsolog.tr("Каждую неделю")
        }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: WEEKLY
    }
}

/**
 * The copies are "QSO-LOG-backup-2026-11-08-1530.adi" (UTF-8 ADIF of the whole log); the newest [KEEP] stay, older
 * ones are deleted. A copy is made at start when the last one is older than the period, and by "Сделать копию сейчас".
 */
object Backups {
    const val KEEP = 10
    const val PREFIX = "QSO-LOG-backup-"
    private val NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    fun fileName(now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)) = PREFIX + now.format(NAME_TIME) + ".adi"

    fun isBackup(name: String) = name.startsWith(PREFIX) && name.endsWith(".adi")

    /** A new copy is due: the period is on and the last copy is older than it (or there is none). */
    fun due(period: BackupPeriod, lastMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        period != BackupPeriod.OFF && (lastMs <= 0 || nowMs - lastMs >= period.days * 86_400_000L - 3_600_000L)

    /** Copies beyond the newest [keep]: to be deleted. Names sort by time. */
    fun excess(names: List<String>, keep: Int = KEEP): List<String> = names.filter(::isBackup).sortedDescending().drop(keep)
}
