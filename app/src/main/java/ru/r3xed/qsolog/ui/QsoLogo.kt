package ru.r3xed.qsolog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.R

private val RussoOne = FontFamily(Font(R.font.russo_one))

/** The app name as a logo: QSO-LOG in Russo One, slanted 8°, with the hyphen in the accent colour. */
@Composable
fun QsoLogo(modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp) {
    Text(
        buildAnnotatedString {
            append("QSO")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("-") }
            append("LOG")
        },
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        fontFamily = RussoOne,
        fontSize = fontSize,
        letterSpacing = 0.02.em,
        maxLines = 1,
        // tan(8°) ≈ 0.14; Android skews to the right for negative values.
        style = MaterialTheme.typography.headlineMedium.copy(textGeometricTransform = TextGeometricTransform(skewX = -0.14f)),
    )
}
