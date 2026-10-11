package lt.tsx.tennarc.tv.roku

import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.queryParam
import lt.tsx.tennarc.tv.TvButton

internal val TvButton.ecpKey: String?
    get() = when (this) {
        TvButton.UP -> "Up"
        TvButton.DOWN -> "Down"
        TvButton.LEFT -> "Left"
        TvButton.RIGHT -> "Right"
        TvButton.ENTER -> "Select"
        TvButton.BACK -> "Back"
        TvButton.HOME, TvButton.EXIT -> "Home"
        TvButton.INFO, TvButton.QMENU -> "Info"
        TvButton.PLAY, TvButton.PAUSE -> "Play"
        TvButton.REWIND -> "Rev"
        TvButton.FAST_FORWARD -> "Fwd"
        TvButton.CHANNEL_UP -> "ChannelUp"
        TvButton.CHANNEL_DOWN -> "ChannelDown"
        else -> null
    }

internal fun literalKey(character: String) = "Lit_" + queryParam(character)

internal fun TextEdit.ecpKeys(): List<String> =
    List(delete) { "Backspace" } + insert.codePoints().toArray().map { literalKey(String(Character.toChars(it))) }
