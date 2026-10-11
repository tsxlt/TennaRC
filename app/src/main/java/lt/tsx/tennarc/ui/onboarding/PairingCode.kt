package lt.tsx.tennarc.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lt.tsx.tennarc.R
import lt.tsx.tennarc.ui.components.FocusWhenSettled
import lt.tsx.tennarc.ui.components.LocalHaptics
import lt.tsx.tennarc.ui.components.focusRim
import lt.tsx.tennarc.ui.components.panelGlass
import lt.tsx.tennarc.ui.theme.glass
import lt.tsx.tennarc.ui.theme.animateColorBetween

internal const val ANDROID_TV_CODE_LENGTH = 6

@Composable
internal fun PairingCodeBoxes(
    ready: Boolean,
    wrong: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = ANDROID_TV_CODE_LENGTH,
    digitsOnly: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    var code by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (!wrong) return@LaunchedEffect
        haptics.reject()
        code = ""
        sent = ""
        shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 1_500f), initialVelocity = 450f)
    }
    LaunchedEffect(code, ready) {
        if (ready && code.length == length && code != sent) {
            sent = code
            onSubmit(code)
        }
    }
    val focus = remember { FocusRequester() }
    FocusWhenSettled(focus)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.pair_code_label)
    BasicTextField(
        value = code,
        onValueChange = { typed ->
            val clean = typed.uppercase().filter { if (digitsOnly) it.isDigit() else it in HEX }.take(length)
            if (clean.length > code.length) haptics.tick()
            if (clean.length < length) sent = ""
            code = clean
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = if (digitsOnly) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
            keyboardType = if (digitsOnly) KeyboardType.NumberPassword else KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        interactionSource = interaction,
        modifier = modifier
            .focusRequester(focus)
            .semantics { contentDescription = label }
            .graphicsLayer { translationX = shake.value.dp.toPx() },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val active = focused && i == code.length.coerceAtMost(length - 1) && code.length < length
                    val edge = animateColorBetween(active, colors.onSurface.copy(alpha = 0f), colors.onSurface.copy(alpha = ACTIVE_RIM), label = "codeBox")
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 56.dp)
                            .glass(panelGlass(), MaterialTheme.shapes.extraSmall)
                            .focusRim(MaterialTheme.shapes.extraSmall) { edge },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.getOrNull(i)?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    }
                }
            }
        },
    )
}

private const val HEX = "0123456789ABCDEF"

private const val ACTIVE_RIM = 0.7f
