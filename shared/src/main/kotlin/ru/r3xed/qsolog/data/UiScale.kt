package ru.r3xed.qsolog.data

/**
 * "Масштаб интерфейса": 100% is the usual size. The smaller steps shrink gaps, buttons and fields by [factor] but the
 * text less ([text]), so it stays readable while more fits on the screen; the card lays its parts out in two columns
 * once there is room. The phone has 100 / 70 / 50, the computer (already drawn more compact) 100 / 85 / 70.
 */
enum class UiScale(val percent: Int, val factor: Float, val text: Float) {
    S100(100, 1f, 1f),
    S85(85, 0.85f, 0.9f),
    S70(70, 0.7f, 0.8f),
    S50(50, 0.5f, 0.65f);

    val label get() = "$percent%"

    companion object {
        val PHONE = listOf(S100, S70, S50)
        val DESKTOP = listOf(S100, S85, S70)

        fun of(percent: Int, steps: List<UiScale>): UiScale = steps.firstOrNull { it.percent == percent } ?: S100

        /** ⌘+ / Ctrl+: the next bigger step, or the same at 100%. */
        fun bigger(s: UiScale, steps: List<UiScale>): UiScale = steps.getOrNull(steps.indexOf(s) - 1) ?: steps.first()

        /** ⌘− / Ctrl−: the next smaller step, or the same at the smallest. */
        fun smaller(s: UiScale, steps: List<UiScale>): UiScale = steps.getOrNull(steps.indexOf(s) + 1) ?: steps.last()
    }
}
