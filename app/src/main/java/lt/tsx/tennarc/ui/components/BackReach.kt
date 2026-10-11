package lt.tsx.tennarc.ui.components

fun backReach(progress: Float): Float {
    val left = 1f - progress.coerceIn(0f, 1f)
    return 1f - left * left * left
}
