package ru.r3xed.qsolog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val RussoOne = FontFamily(Font("font/russo_one.ttf"))

/** The app name as a logo: QSO-LOG in Russo One, slanted 8°, with the hyphen in the accent colour. */
@Composable
fun QsoLogo(modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp) {
    Text(
        buildAnnotatedString {
            append("QSO")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("-") }
            append("LOG")
        },
        // Skia on desktop ignores TextGeometricTransform.skewX, so the slant is applied while drawing:
        // x' = x - k·y + k·h keeps the baseline in place and leans the tops to the right; tan(8°) ≈ 0.14.
        modifier = modifier
            .padding(end = 6.dp)
            .drawWithContent {
                val k = 0.14f
                val m = Matrix().apply {
                    values[Matrix.SkewX] = -k
                    values[Matrix.TranslateX] = k * size.height
                }
                withTransform({ transform(m) }) { this@drawWithContent.drawContent() }
            },
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = RussoOne,
        fontSize = fontSize,
        letterSpacing = 0.02.em,
        maxLines = 1,
        style = MaterialTheme.typography.headlineMedium,
    )
}
