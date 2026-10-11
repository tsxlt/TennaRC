package lt.tsx.tennarc.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkTest {
    private fun icon(draw: Canvas.(Paint) -> Unit): ImageBitmap =
        ImageBitmap(128, 128).also { Canvas(it).draw(Paint().apply { color = Color.Red }) }

    @Test
    fun aWholeTileIsNoMark() {
        assertFalse(isMark(icon { drawRect(0f, 0f, 128f, 128f, it) }))
    }

    @Test
    fun roundedCornersDontMakeAMark() {
        assertFalse(isMark(icon { drawRoundRect(0f, 0f, 128f, 128f, 28f, 28f, it) }))
    }

    @Test
    fun aShapeOnNothingIsAMark() {
        assertTrue(isMark(icon { drawCircle(Offset(64f, 64f), 40f, it) }))
        assertTrue(isMark(icon { drawRoundRect(0f, 28f, 128f, 100f, 24f, 24f, it) }))
    }
}
