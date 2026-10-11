package lt.tsx.tennarc.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val LUCIDE_STROKE = 1.75f

internal fun lucide(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
    val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (path in paths) {
        builder.addPath(
            PathParser().parsePathString(path).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = LUCIDE_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

internal fun ImageVector.heavier(stroke: Float): ImageVector {
    val builder = ImageVector.Builder(name = "${name}_heavy", defaultWidth = defaultWidth, defaultHeight = defaultHeight, viewportWidth = viewportWidth, viewportHeight = viewportHeight)
    for (node in root) {
        if (node is VectorPath) {
            builder.addPath(node.pathData, fill = node.fill, stroke = node.stroke, strokeLineWidth = stroke, strokeLineCap = node.strokeLineCap, strokeLineJoin = node.strokeLineJoin)
        }
    }
    return builder.build()
}

// Lucide (ISC, some via Feather's MIT; assets/licenses/lucide.txt), from lucide-static.
object Lucide {
    val Undo2: ImageVector by lazy {
        lucide(
            "undo-2",
            "M9 14L4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
        )
    }

    val House: ImageVector by lazy {
        lucide(
            "house",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 0.709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val Keyboard: ImageVector by lazy {
        lucide(
            "keyboard",
            "M10 8h0.01",
            "M12 12h0.01",
            "M14 8h0.01",
            "M16 12h0.01",
            "M18 8h0.01",
            "M6 8h0.01",
            "M7 16h10",
            "M8 12h0.01",
            "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        )
    }

    val Ellipsis: ImageVector by lazy {
        lucide(
            "ellipsis",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M18 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M4 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        )
    }

    val Rewind: ImageVector by lazy {
        lucide(
            "rewind",
            "M12 6a2 2 0 0 0-3.414-1.414l-6 6a2 2 0 0 0 0 2.828l6 6A2 2 0 0 0 12 18z",
            "M22 6a2 2 0 0 0-3.414-1.414l-6 6a2 2 0 0 0 0 2.828l6 6A2 2 0 0 0 22 18z",
        )
    }

    val FastForward: ImageVector by lazy {
        lucide(
            "fast-forward",
            "M12 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 12 18z",
            "M2 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 2 18z",
        )
    }

    val Minus: ImageVector by lazy { lucide("minus", "M5 12h14") }

    val Plus: ImageVector by lazy { lucide("plus", "M5 12h14", "M12 5v14") }

    val Volume2: ImageVector by lazy {
        lucide(
            "volume-2",
            "M11 4.702a0.705 0.705 0 0 0-1.203-0.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416" +
                "a1.4 1.4 0 0 1 0.997 0.413l3.383 3.384A0.705 0.705 0 0 0 11 19.298z",
            "M16 9a5 5 0 0 1 0 6",
            "M19.364 18.364a9 9 0 0 0 0-12.728",
        )
    }

    val VolumeX: ImageVector by lazy {
        lucide(
            "volume-x",
            "M11 4.702a0.7 0.7 0 0 0-1.203-0.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416" +
                "a1.4 1.4 0 0 1 0.997 0.413l3.383 3.384A0.7 0.7 0 0 0 11 19.298z",
            "M16.5 14.5l5-5",
            "M16.5 9.5l5 5",
        )
    }

    val Play: ImageVector by lazy {
        lucide(
            "play",
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 0.003 3.458l-12 7A2 2 0 0 1 5 19z",
            filled = true,
        )
    }

    val Settings: ImageVector by lazy {
        lucide(
            "settings",
            "M12.22 2h-0.44a2 2 0 0 0-2 2v0.18a2 2 0 0 1-1 1.73l-0.43 0.25a2 2 0 0 1-2 0l-0.15-0.08a2 2 0 0 0-2.73 0.73" +
                "l-0.22 0.38a2 2 0 0 0 0.73 2.73l0.15 0.1a2 2 0 0 1 1 1.72v0.51a2 2 0 0 1-1 1.74l-0.15 0.09a2 2 0 0 0-0.73 2.73" +
                "l0.22 0.38a2 2 0 0 0 2.73 0.73l0.15-0.08a2 2 0 0 1 2 0l0.43 0.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h0.44" +
                "a2 2 0 0 0 2-2v-0.18a2 2 0 0 1 1-1.73l0.43-0.25a2 2 0 0 1 2 0l0.15 0.08a2 2 0 0 0 2.73-0.73l0.22-0.39" +
                "a2 2 0 0 0-0.73-2.73l-0.15-0.08a2 2 0 0 1-1-1.74v-0.5a2 2 0 0 1 1-1.74l0.15-0.09a2 2 0 0 0 0.73-2.73" +
                "l-0.22-0.38a2 2 0 0 0-2.73-0.73l-0.15 0.08a2 2 0 0 1-2 0l-0.43-0.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        )
    }

    val Menu: ImageVector by lazy { lucide("menu", "M4 6h16", "M4 12h16", "M4 18h16") }

    val LogIn: ImageVector by lazy {
        lucide("log-in", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4", "M10 17l5-5l-5-5", "M15 12H3")
    }

    val LogOut: ImageVector by lazy {
        lucide("log-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5l-5-5", "M21 12H9")
    }

    val Info: ImageVector by lazy {
        lucide(
            "info",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M12 16v-4",
            "M12 8h0.01",
        )
    }

    val Square: ImageVector by lazy {
        lucide("square", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z")
    }

    val Captions: ImageVector by lazy {
        lucide(
            "captions",
            "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
            "M7 15h4",
            "M15 15h2",
            "M7 11h2",
            "M13 11h4",
        )
    }

    val AudioLines: ImageVector by lazy {
        lucide("audio-lines", "M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
    }

    val Sun: ImageVector by lazy {
        lucide(
            "sun",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
            "M12 2v2",
            "M12 20v2",
            "M4.93 4.93l1.41 1.41",
            "M17.66 17.66l1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "M6.34 17.66l-1.41 1.41",
            "M19.07 4.93l-1.41 1.41",
        )
    }

    val Image: ImageVector by lazy {
        lucide(
            "image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M21 15l-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    val Speaker: ImageVector by lazy {
        lucide(
            "speaker",
            "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
            "M12 6h0.01",
            "M8 14a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
            "M12 14h0.01",
        )
    }

    val Delete: ImageVector by lazy {
        lucide("delete", "M20 5H9l-7 7l7 7h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "M18 9l-6 6", "M12 9l6 6")
    }

    val Pause: ImageVector by lazy {
        lucide(
            "pause",
            "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            filled = true,
        )
    }

    val Lamp: ImageVector by lazy {
        lucide(
            "lamp",
            "M12 12v6",
            "M4.077 10.615A1 1 0 0 0 5 12h14a1 1 0 0 0 0.923-1.385l-3.077-7.384A2 2 0 0 0 15 2H9a2 2 0 0 0-1.846 1.23z",
            "M8 20a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1z",
        )
    }

    val Mic: ImageVector by lazy {
        lucide(
            "mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M12 2a3 3 0 0 1 3 3v7a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3z",
        )
    }

    val ChevronDown: ImageVector by lazy { lucide("chevron-down", "M6 9l6 6l6-6") }

    val WifiLow: ImageVector by lazy { lucide("wifi-low", "M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0") }

    val WifiOuter: ImageVector by lazy { lucide("wifi-outer", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0") }

    val Tv: ImageVector by lazy {
        lucide("tv", "M17 2l-5 5l-5-5", "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z")
    }

    val Globe: ImageVector by lazy {
        lucide("globe", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0", "M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0-20", "M2 12h20")
    }

    val Images: ImageVector by lazy {
        lucide(
            "images",
            "M22 11l-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16",
            "M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2",
            "M12 7a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M10 2h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
        )
    }

    val Music: ImageVector by lazy {
        lucide("music", "M9 18V5l12-2v13", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M15 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    }

    val ShoppingBag: ImageVector by lazy {
        lucide(
            "shopping-bag",
            "M16 10a4 4 0 0 1-8 0",
            "M3.103 6.034h17.794",
            "M3.4 5.467a2 2 0 0 0-0.4 1.2V20a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6.667a2 2 0 0 0-0.4-1.2l-2-2.667A2 2 0 0 0 17 2H7a2 2 0 0 0-1.6 0.8z",
        )
    }

    val Cast: ImageVector by lazy {
        lucide(
            "cast",
            "M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6",
            "M2 12a9 9 0 0 1 8 8",
            "M2 16a5 5 0 0 1 4 4",
            "M2 20h0.01",
        )
    }

    val Monitor: ImageVector by lazy {
        lucide("monitor", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 21h8", "M12 17v4")
    }

    val ScreenShare: ImageVector by lazy {
        lucide(
            "screen-share",
            "M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3",
            "M8 21h8",
            "M12 17v4",
            "M17 8l5-5",
            "M17 3h5v5",
        )
    }
}
