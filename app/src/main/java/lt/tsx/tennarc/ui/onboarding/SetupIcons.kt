package lt.tsx.tennarc.ui.onboarding

import androidx.compose.ui.graphics.vector.ImageVector
import lt.tsx.tennarc.ui.components.lucide

// Lucide (ISC; assets/licenses/lucide.txt).
internal object SetupIcons {
    val Tv: ImageVector by lazy {
        lucide(
            "tv",
            "m17 2-5 5-5-5",
            "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
        )
    }

    val Wifi: ImageVector by lazy {
        lucide("wifi", "M12 20h0.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0")
    }

    val WifiOff: ImageVector by lazy {
        lucide(
            "wifi-off",
            "M12 20h0.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
            "m2 2 20 20",
        )
    }

    val Check: ImageVector by lazy { lucide("check", "M20 6 9 17l-5-5") }

    val ChevronRight: ImageVector by lazy { lucide("chevron-right", "m9 18 6-6-6-6") }

    val ArrowRight: ImageVector by lazy { lucide("arrow-right", "M5 12h14", "m12 5 7 7-7 7") }
}
