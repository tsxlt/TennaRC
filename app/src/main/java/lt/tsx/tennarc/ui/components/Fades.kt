package lt.tsx.tennarc.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.fadeTopEdge(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = if (scroll.value > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithCache {
        val fade = TOP_FADE.toPx()
        val brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = fade)
        onDrawWithContent {
            drawContent()
            val amount = (scroll.value / fade).coerceIn(0f, 1f)
            if (amount > 0f) drawRect(brush, size = Size(size.width, fade), alpha = amount, blendMode = BlendMode.DstOut)
        }
    }

fun Modifier.fadeEnds(list: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = if (list.canScrollBackward || list.canScrollForward) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
    .drawWithCache {
        val fade = TOP_FADE.toPx()
        val top = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = fade)
        val bottom = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = size.height - fade, endY = size.height)
        onDrawWithContent {
            drawContent()
            val info = list.layoutInfo
            val above = if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / fade).coerceIn(0f, 1f)
            val last = info.visibleItemsInfo.lastOrNull()
            val below = when {
                last == null -> 0f
                last.index < info.totalItemsCount - 1 -> 1f
                else -> ((last.offset + last.size + info.afterContentPadding - info.viewportEndOffset) / fade).coerceIn(0f, 1f)
            }
            if (above > 0f) drawRect(top, size = Size(size.width, fade), alpha = above, blendMode = BlendMode.DstOut)
            if (below > 0f) drawRect(bottom, Offset(0f, size.height - fade), Size(size.width, fade), alpha = below, blendMode = BlendMode.DstOut)
        }
    }

private val TOP_FADE = 24.dp
