package lt.tsx.tennarc.ui.theme

import android.icu.lang.UCharacter
import android.icu.util.ULocale
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TennaColors {
    val Ink = Color(0xFF0C0D10)
    val Panel = Color(0xFF15171B)
    val Raised = Color(0xFF1C1F24)
    val RaisedHigh = Color(0xFF24282E)
    val RaisedHighest = Color(0xFF2D3239)
    val Line = Color(0xFF656C76)
    val LineSoft = Color(0xFF23272D)
    val Text = Color(0xFFEDEEF0)
    val TextDim = Color(0xFF9BA1AB)
    val Ember = Color(0xFFFF8A4C)
    val EmberDeep = Color(0xFF3B2115)
    val OnEmber = Color(0xFF1E0E06)
    val OnEmberDeep = Color(0xFFFFD3BD)
    val Mint = Color(0xFF5ED69B)
    val Amber = Color(0xFFFFC857)
    val Coral = Color(0xFFFF6B6B)
    val CoralDeep = Color(0xFF3D1A1A)

    val TvRed = Color(0xFFE5484D)
    val TvGreen = Color(0xFF3DBE6E)
    val TvYellow = Color(0xFFF5C33B)
    val TvBlue = Color(0xFF3E8BF0)
}

object TennaLight {
    val Ink = Color(0xFFF3F4F6)
    val Panel = Color(0xFFFFFFFF)
    val Raised = Color(0xFFECEEF1)
    val RaisedHigh = Color(0xFFE3E6EA)
    val RaisedHighest = Color(0xFFD7DBE0)
    val Line = Color(0xFF777E88)
    val LineSoft = Color(0xFFDADDE2)
    val Text = Color(0xFF16181C)
    val TextDim = Color(0xFF5A616B)
    val Ember = Color(0xFFD65C1E)
    val EmberDeep = Color(0xFFFFE3D4)
    val OnEmber = Color(0xFFFFFFFF)
    val OnEmberDeep = Color(0xFF6A2A0C)
    val Mint = Color(0xFF1B9660)
    val Amber = Color(0xFFA86D00)
    val Coral = Color(0xFFCF3434)
    val CoralDeep = Color(0xFFFCE3E3)
}

private val Dark = darkColorScheme(
    primary = TennaColors.Ember,
    onPrimary = TennaColors.OnEmber,
    primaryContainer = TennaColors.EmberDeep,
    onPrimaryContainer = TennaColors.OnEmberDeep,
    secondary = TennaColors.TextDim,
    onSecondary = TennaColors.Ink,
    secondaryContainer = TennaColors.RaisedHighest,
    onSecondaryContainer = TennaColors.Text,
    tertiary = TennaColors.Mint,
    background = TennaColors.Ink,
    onBackground = TennaColors.Text,
    surface = TennaColors.Ink,
    onSurface = TennaColors.Text,
    surfaceVariant = TennaColors.Raised,
    onSurfaceVariant = TennaColors.TextDim,
    surfaceContainerLowest = TennaColors.Ink,
    surfaceContainerLow = TennaColors.Panel,
    surfaceContainer = TennaColors.Raised,
    surfaceContainerHigh = TennaColors.RaisedHigh,
    surfaceContainerHighest = TennaColors.RaisedHighest,
    inverseSurface = TennaColors.Text,
    inverseOnSurface = TennaColors.Ink,
    outline = TennaColors.Line,
    outlineVariant = TennaColors.LineSoft,
    error = TennaColors.Coral,
    onError = TennaColors.Ink,
    errorContainer = TennaColors.CoralDeep,
    onErrorContainer = TennaColors.Coral,
    scrim = Color.Black,
)

private val Light = lightColorScheme(
    primary = TennaLight.Ember,
    onPrimary = TennaLight.OnEmber,
    primaryContainer = TennaLight.EmberDeep,
    onPrimaryContainer = TennaLight.OnEmberDeep,
    secondary = TennaLight.TextDim,
    onSecondary = TennaLight.Panel,
    secondaryContainer = TennaLight.RaisedHighest,
    onSecondaryContainer = TennaLight.Text,
    tertiary = TennaLight.Mint,
    background = TennaLight.Ink,
    onBackground = TennaLight.Text,
    surface = TennaLight.Ink,
    onSurface = TennaLight.Text,
    surfaceVariant = TennaLight.Raised,
    onSurfaceVariant = TennaLight.TextDim,
    surfaceContainerLowest = TennaLight.Panel,
    surfaceContainerLow = TennaLight.Panel,
    surfaceContainer = TennaLight.Raised,
    surfaceContainerHigh = TennaLight.RaisedHigh,
    surfaceContainerHighest = TennaLight.RaisedHighest,
    inverseSurface = Color(0xFF2A2D33),
    inverseOnSurface = TennaLight.Ink,
    outline = TennaLight.Line,
    outlineVariant = TennaLight.LineSoft,
    error = TennaLight.Coral,
    onError = TennaLight.Panel,
    errorContainer = TennaLight.CoralDeep,
    onErrorContainer = TennaLight.Coral,
    scrim = Color.Black,
)

const val THEME_FADE_MS = 700

private val DARK_GROUND = TennaColors.Ink.luminance()
private val LIGHT_GROUND = TennaLight.Ink.luminance()

val ColorScheme.darkness: Float
    get() = ((LIGHT_GROUND - background.luminance()) / (LIGHT_GROUND - DARK_GROUND)).coerceIn(0f, 1f)

val ColorScheme.scrimAlpha: Float get() = androidx.compose.ui.util.lerp(0.3f, 0.6f, darkness)

val ColorScheme.warning: Color get() = lerp(TennaLight.Amber, TennaColors.Amber, darkness)

private fun mix(light: ColorScheme, dark: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return light
    if (fraction >= 1f) return dark
    fun m(a: Color, b: Color) = lerp(a, b, fraction)
    return (if (fraction < 0.5f) light else dark).copy(
        primary = m(light.primary, dark.primary),
        onPrimary = m(light.onPrimary, dark.onPrimary),
        primaryContainer = m(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = m(light.onPrimaryContainer, dark.onPrimaryContainer),
        inversePrimary = m(light.inversePrimary, dark.inversePrimary),
        secondary = m(light.secondary, dark.secondary),
        onSecondary = m(light.onSecondary, dark.onSecondary),
        secondaryContainer = m(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = m(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiary = m(light.tertiary, dark.tertiary),
        onTertiary = m(light.onTertiary, dark.onTertiary),
        tertiaryContainer = m(light.tertiaryContainer, dark.tertiaryContainer),
        onTertiaryContainer = m(light.onTertiaryContainer, dark.onTertiaryContainer),
        background = m(light.background, dark.background),
        onBackground = m(light.onBackground, dark.onBackground),
        surface = m(light.surface, dark.surface),
        onSurface = m(light.onSurface, dark.onSurface),
        surfaceVariant = m(light.surfaceVariant, dark.surfaceVariant),
        onSurfaceVariant = m(light.onSurfaceVariant, dark.onSurfaceVariant),
        surfaceTint = m(light.surfaceTint, dark.surfaceTint),
        inverseSurface = m(light.inverseSurface, dark.inverseSurface),
        inverseOnSurface = m(light.inverseOnSurface, dark.inverseOnSurface),
        error = m(light.error, dark.error),
        onError = m(light.onError, dark.onError),
        errorContainer = m(light.errorContainer, dark.errorContainer),
        onErrorContainer = m(light.onErrorContainer, dark.onErrorContainer),
        outline = m(light.outline, dark.outline),
        outlineVariant = m(light.outlineVariant, dark.outlineVariant),
        scrim = m(light.scrim, dark.scrim),
        surfaceBright = m(light.surfaceBright, dark.surfaceBright),
        surfaceDim = m(light.surfaceDim, dark.surfaceDim),
        surfaceContainer = m(light.surfaceContainer, dark.surfaceContainer),
        surfaceContainerHigh = m(light.surfaceContainerHigh, dark.surfaceContainerHigh),
        surfaceContainerHighest = m(light.surfaceContainerHighest, dark.surfaceContainerHighest),
        surfaceContainerLow = m(light.surfaceContainerLow, dark.surfaceContainerLow),
        surfaceContainerLowest = m(light.surfaceContainerLowest, dark.surfaceContainerLowest),
    )
}

private val Base = Typography()

private val Type = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val Shape = Shapes(
    extraSmall = SquircleShape(CornerExtraSmall),
    small = SquircleShape(CornerSmall),
    medium = SquircleShape(CornerMedium),
    large = SquircleShape(CornerLarge),
    extraLarge = SquircleShape(CornerExtraLarge),
)

val SectionLabel: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)

@Composable
fun sectionCase(text: String): String {
    if (text.any { it in '\u10D0'..'\u10FF' }) return text
    return UCharacter.toUpperCase(ULocale.forLocale(LocalConfiguration.current.locales[0]), text)
}

fun slightlySmaller(style: TextStyle): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = style.fontSize * 0.85f, maxFontSize = style.fontSize, stepSize = 0.5.sp)

@Composable
fun TennaTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val fraction by animateFloatAsState(if (dark) 1f else 0f, tween(THEME_FADE_MS, easing = FastOutSlowInEasing), label = "theme")
    val scheme = remember(fraction) { mix(Light, Dark, fraction) }
    MaterialTheme(colorScheme = scheme, typography = Type, shapes = Shape, content = content)
}

@Composable
fun animateColorBetween(isOn: Boolean, off: Color, on: Color, spec: AnimationSpec<Float> = spring(), label: String = "colorBetween"): Color {
    val fraction by animateFloatAsState(if (isOn) 1f else 0f, spec, label = label)
    return lerp(off, on, fraction)
}
