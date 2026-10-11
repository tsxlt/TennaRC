package lt.tsx.tennarc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.touchpadIn
import lt.tsx.tennarc.data.touchpadOn
import lt.tsx.tennarc.data.tvKey
import lt.tsx.tennarc.data.withPad
import lt.tsx.tennarc.tv.RemoteActions

enum class PadMode { DPAD, TOUCHPAD }

@Composable
internal fun rememberPadMode(prefs: Prefs, app: String?, initial: PadMode?, actions: RemoteActions): MutableState<PadMode> {
    val tv = prefs.tvKey
    val latestPrefs by rememberUpdatedState(prefs)
    val leftHere = if (app?.let { prefs.touchpadIn(tv, it) } ?: prefs.touchpadOn(tv)) PadMode.TOUCHPAD else PadMode.DPAD
    val mode = rememberSaveable { mutableStateOf(initial ?: leftHere) }
    var modeTv by rememberSaveable { mutableStateOf(tv) }
    var modeApp by rememberSaveable { mutableStateOf(app) }
    if (modeTv != tv) {
        modeTv = tv
        modeApp = app
        mode.value = leftHere
    } else if (app != null && app != modeApp) {
        modeApp = app
        prefs.touchpadIn(tv, app)?.let { mode.value = if (it) PadMode.TOUCHPAD else PadMode.DPAD }
    }
    val touchpad = mode.value == PadMode.TOUCHPAD
    val savedApp = modeApp
    LaunchedEffect(tv, touchpad, savedApp) {
        if (latestPrefs.withPad(tv, touchpad, savedApp) != latestPrefs) actions.updatePrefs { it.withPad(tv, touchpad, savedApp) }
    }
    return mode
}
