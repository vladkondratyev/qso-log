package ru.r3xed.qsolog.data

/**
 * "Масштаб интерфейса": 100% is the usual size, from 50% to 150% by 10%. The smaller steps shrink gaps, buttons and
 * fields by [factor] but the text less ([text]), so it stays readable while more fits on the screen; the card lays its
 * parts out in two columns once there is room. Above 100% everything, the text too, grows alike.
 */
enum class UiScale(val percent: Int) {
    S150(150), S140(140), S130(130), S120(120), S110(110), S100(100),
    S90(90), S80(80), S70(70), S60(60), S50(50);

    val factor: Float get() = percent / 100f

    /** The text shrinks by 70% of what the rest does (85% → 0.9, 70% → 0.8, 50% → 0.65), grows as the rest. */
    val text: Float get() = if (factor >= 1f) factor else 1f - 0.7f * (1f - factor)

    val label get() = "$percent%"

    companion object {
        /** All the steps, the biggest first. */
        val STEPS = entries.toList()
        val PHONE = STEPS
        val DESKTOP = STEPS

        /** The step of [percent]; the 85% of older versions goes to the nearest one (90%). */
        fun of(percent: Int, steps: List<UiScale> = STEPS): UiScale {
            val p = ((percent + 5) / 10 * 10).coerceIn(50, 150)
            return steps.firstOrNull { it.percent == p } ?: S100
        }

        /** ⌘+ / Ctrl+: the next bigger step, or the same at the biggest. */
        fun bigger(s: UiScale, steps: List<UiScale> = STEPS): UiScale = steps.getOrNull(steps.indexOf(s) - 1) ?: steps.first()

        /** ⌘− / Ctrl−: the next smaller step, or the same at the smallest. */
        fun smaller(s: UiScale, steps: List<UiScale> = STEPS): UiScale = steps.getOrNull(steps.indexOf(s) + 1) ?: steps.last()
    }
}
