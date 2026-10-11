package lt.tsx.tennarc.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasStartActivityClickAction
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import lt.tsx.tennarc.MainActivity
import lt.tsx.tennarc.R
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.WEBOS_FEATURES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xhdpi")
class TvWidgetTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(tvName = "Tenna", host = "h", mac = "02:00:00:00:00:01")
    private val apps = listOf(
        TvApp(TvApps.YOUTUBE, "YouTube", "http://h/icons/youtube.png"),
        TvApp(TvApps.NETFLIX, "Netflix", "http://h/icons/netflix.png"),
        TvApp("amazon", "Prime Video", "http://h/icons/prime.png"),
        TvApp("disney", "Disney+", "http://h/icons/disney.png"),
        TvApp("spotify", "Spotify", "http://h/icons/spotify.png"),
        TvApp(TvApps.BROWSER, "Web Browser", "http://h/icons/browser.png"),
    )
    private val on = TvState(host = "h", connection = Connection.Connected, power = TvPower.ON, apps = apps)
    private val use = prefs.copy(appUse = mapOf(TvApps.NETFLIX to 20f, "spotify" to 12f, "disney" to 8f, "amazon" to 5f))
    private val playing = widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), use)
    private val off = widgetModel(on.copy(power = TvPower.STANDBY), use)
    private val unpaired = widgetModel(TvState(host = "h", connection = Connection.NeedsPairing()), prefs)
    private val cast = widgetModel(on.copy(cast = CastState("Big Buck Bunny", CastPhase.PAUSED)), use)
    private val unknown = widgetModel(TvState(host = "h", apps = apps), use)
    private val twoTvs = use.copy(otherTvs = listOf(SavedTv("Bedroom", "192.168.1.50", TvKind.ROKU, "")))

    private val icons = object : IconLoader {
        private val drawn = mutableMapOf<String, ImageBitmap>()

        override suspend fun load(url: String): ImageBitmap? = cached(url)

        override fun cached(url: String): ImageBitmap? {
            val (fill, ink, letter) = when {
                "youtube" in url -> Triple(0xFFE62117.toInt(), 0xFFFFFFFF.toInt(), "▶")
                "netflix" in url -> Triple(0xFF141414.toInt(), 0xFFE50914.toInt(), "N")
                "prime" in url -> Triple(0xFF1B98E0.toInt(), 0xFFFFFFFF.toInt(), "p")
                "disney" in url -> Triple(0xFF0E1E5B.toInt(), 0xFFFFFFFF.toInt(), "D+")
                "spotify" in url -> Triple(0xFF1DB954.toInt(), 0xFF101010.toInt(), "S")
                else -> return null
            }
            return drawn.getOrPut(url) {
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(fill)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    textSize = 48f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText(letter, 48f, 48f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
                bitmap.asImageBitmap()
            }
        }
    }

    private val art get() = WidgetArt(icons, context.resources.displayMetrics.density)

    private fun check(model: WidgetModel, size: DpSize, armed: Boolean = false, block: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            provideComposable { TvWidgetContent(model, size, armed, waking = false, art = art) }
            block()
        }

    private fun GlanceAppWidgetUnitTest.has(description: String) {
        onNode(hasContentDescriptionEqualTo(description)).assertExists()
    }

    private fun GlanceAppWidgetUnitTest.lacks(description: String) {
        onNode(hasContentDescriptionEqualTo(description)).assertDoesNotExist()
    }

    private fun GlanceAppWidgetUnitTest.sends(description: String, key: WidgetKey) {
        onNode(hasContentDescriptionEqualTo(description))
            .assertHasRunCallbackClickAction<WidgetKeyAction>(actionParametersOf(KEY_PARAM to key.name))
    }

    private fun GlanceAppWidgetUnitTest.opensOnTv(title: String, appId: String) {
        onNode(hasContentDescriptionEqualTo(title))
            .assertHasRunCallbackClickAction<WidgetLaunchAppAction>(actionParametersOf(APP_PARAM to appId))
    }

    private fun GlanceAppWidgetUnitTest.text(text: String) {
        onNode(hasText(text)).assertExists()
    }

    private fun GlanceAppWidgetUnitTest.opensApp(text: String) {
        onNode(hasClickAction() and hasAnyDescendant(hasText(text))).assertHasStartActivityClickAction<MainActivity>()
    }

    private val compact = DpSize(176.dp, 92.dp)
    private val square = DpSize(176.dp, 196.dp)
    private val row = DpSize(338.dp, 92.dp)
    private val row3 = DpSize(253.dp, 92.dp)
    private val card = DpSize(338.dp, 196.dp)
    private val shortCard = DpSize(338.dp, 150.dp)
    private val card3 = DpSize(253.dp, 196.dp)

    private val hyperOs = DpSize(338.dp, 260.dp)
    private val tall = DpSize(338.dp, 320.dp)

    @Test
    fun `compact is play and volume`() = check(playing, compact) {
        sends("Pause", WidgetKey.PLAY_PAUSE)
        sends("Volume down", WidgetKey.VOLUME_DOWN)
        sends("Volume up", WidgetKey.VOLUME_UP)
        lacks("Mute")
        lacks("Turn off")
    }

    @Test
    fun `the row names the TV and what's on`() = check(playing, row) {
        text("Tenna")
        text("YouTube")
        opensApp("YouTube")
        sends("Pause", WidgetKey.PLAY_PAUSE)
        has("Volume up")
        lacks("Mute")
        lacks("Netflix")
    }

    @Test
    fun `the card has mute and power, and favourites that open on the TV`() = check(playing, card) {
        text("YouTube")
        opensApp("YouTube")
        sends("Mute", WidgetKey.MUTE)
        sends("Turn off", WidgetKey.POWER)
        sends("Pause", WidgetKey.PLAY_PAUSE)
        sends("Volume down", WidgetKey.VOLUME_DOWN)
        opensOnTv("Netflix", TvApps.NETFLIX)
        opensOnTv("Spotify", "spotify")
        opensOnTv("Disney+", "disney")
        opensOnTv("Prime Video", "amazon")
        lacks("YouTube")
    }

    @Test
    fun `a short card has no room for favourites`() = check(playing, shortCard) {
        sends("Mute", WidgetKey.MUTE)
        lacks("Netflix")
    }

    @Test
    fun `the square shows what's on big, play and the volume`() = check(playing, square) {
        text("YouTube")
        opensApp("YouTube")
        sends("Pause", WidgetKey.PLAY_PAUSE)
        sends("Volume up", WidgetKey.VOLUME_UP)
        sends("Turn off", WidgetKey.POWER)
    }

    @Test
    fun `a tall widget with another TV offers it`() = check(widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), twoTvs), hyperOs) {
        onNode(hasContentDescriptionEqualTo("Use Bedroom")).assertHasRunCallbackClickAction<WidgetSwitchTvAction>()
        text("Bedroom")
    }

    @Test
    fun `with one TV there's nothing to switch to`() = check(playing, hyperOs) {
        lacks("Use Bedroom")
    }

    @Test
    fun `an armed power key asks again`() = check(playing, card, armed = true) {
        has("Tap again to power off")
        text("Tap again to power off")
    }

    @Test
    fun `off, every size offers only waking it`() {
        for (size in listOf(compact, square, row, card, hyperOs)) {
            check(off, size) {
                sends("Turn on", WidgetKey.POWER)
                lacks("Pause")
                lacks("Play")
                lacks("Volume up")
                lacks("Netflix")
            }
        }
    }

    @Test
    fun `off with no way to wake it just says so`() = check(off.copy(canWake = false), row) {
        text("TV looks off")
        lacks("Turn on")
    }

    @Test
    fun `unpaired opens the app to pair`() = check(unpaired, card) {
        text("Not paired yet")
        lacks("Play")
        lacks("Turn off")
    }

    private fun render(model: WidgetModel, size: DpSize, armed: Boolean = false, waking: Boolean = false): Bitmap = runBlocking {
        val views = Fixed(model, armed, waking, art).compose(context, size = size)
        val frame = FrameLayout(context)
        frame.addView(views.apply(context, frame))
        draw(frame, size)
    }

    private fun shoot(name: String, model: WidgetModel, size: DpSize, armed: Boolean = false, waking: Boolean = false): Bitmap =
        render(model, size, armed, waking).also { it.captureRoboImage("build/shots/widget/$name.png") }

    private class Fixed(val model: WidgetModel, val armed: Boolean, val waking: Boolean, val art: WidgetArt) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Exact

        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent {
            TvWidgetContent(model, LocalSize.current, armed, waking, art)
        }
    }

    @Test
    fun shots() {
        shoot("compact", playing, compact)
        shoot("compact_off", off, compact)
        shoot("square", playing, square)
        shoot("square_cast", cast, square)
        shoot("square_short", playing, DpSize(176.dp, 120.dp))
        shoot("row", playing, row)
        shoot("row_3x1", playing, row3)
        shoot("row_paused_cast", cast, row)
        shoot("row_unknown", unknown, row)
        shoot("row_off", off, row)
        shoot("row_waking", off, row, waking = true)
        shoot("card", playing, card)
        shoot("card_short", playing, shortCard)
        shoot("card_3x2", playing, card3)
        shoot("card_muted", playing.copy(muted = true), card)
        shoot("card_armed", playing, card, armed = true)
        shoot("card_off", off, card)
        shoot("card_unpaired", unpaired, card)
        shoot("card_no_app_list", widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE, features = WEBOS_FEATURES - Feature.APP_LIST), use), card)
        shoot("hero_hyperos", playing, hyperOs)
        shoot("hero_two_tvs", widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), twoTvs), hyperOs)
        shoot("hero_tall", widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), twoTvs), tall)
    }

    @Test
    fun `a stretched widget has no dead gap`() {
        for (size in listOf(card, hyperOs, tall, square)) {
            val bitmap = render(playing, size)
            val density = context.resources.displayMetrics.density
            val margin = (16 * density).toInt()
            val surface = luminance(WidgetPalette.Dark.surface.toArgb())
            var run = 0
            var longest = 0
            for (y in margin + (20 * density).toInt() until bitmap.height - margin - (20 * density).toInt()) {
                val busy = (margin + (12 * density).toInt() until bitmap.width - margin - (12 * density).toInt()).any { x ->
                    kotlin.math.abs(luminance(bitmap.getPixel(x, y)) - surface) > 0.2f
                }
                run = if (busy) 0 else run + 1
                longest = maxOf(longest, run)
            }
            assertTrue("$size: ${longest / density}dp with nothing in it", longest / density <= 40f)
        }
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    @Test
    fun `contact sheet`() {
        val shapes = listOf(
            "2×1" to (playing to compact),
            "2×2" to (playing to square),
            "3×1" to (playing to row3),
            "4×1" to (playing to row),
            "4×2 short" to (playing to shortCard),
            "3×2" to (playing to card3),
            "4×2" to (playing to card),
            "4×2, no app list" to (widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE, features = WEBOS_FEATURES - Feature.APP_LIST), use) to card),
            "HyperOS 4×2 (338×260)" to (playing to hyperOs),
            "Two TVs, 338×260" to (widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), twoTvs) to hyperOs),
            "4×3, two TVs" to (widgetModel(on.copy(foregroundAppId = TvApps.YOUTUBE), twoTvs) to tall),
            "Cast, paused" to (cast to card),
            "Nothing known" to (unknown to card),
            "Off" to (off to card),
            "Unpaired" to (unpaired to row),
            "Square, cast" to (cast to square),
        )
        val pictures = shapes.flatMap { (caption, shape) ->
            val (model, size) = shape
            listOf("$caption · dark" to render(model.copy(dark = true), size), "$caption · light" to render(model.copy(dark = false), size))
        }
        val density = context.resources.displayMetrics.density
        val caption = (22 * density).toInt()
        val pad = (12 * density).toInt()
        val columns = 4
        val cellWidth = pictures.maxOf { it.second.width }
        val rows = pictures.chunked(columns)
        val height = rows.sumOf { line -> line.maxOf { it.second.height } + caption + pad }
        val sheet = Bitmap.createBitmap(columns * (cellWidth + pad) + pad, height + pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF2A2E36.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8EAED.toInt()
            textSize = 13 * density
        }
        var top = pad
        for (line in rows) {
            line.forEachIndexed { i, (label, picture) ->
                val left = pad + i * (cellWidth + pad)
                canvas.drawText(label, left.toFloat(), top + caption * 0.7f, text)
                canvas.drawBitmap(picture, left.toFloat(), (top + caption).toFloat(), null)
            }
            top += line.maxOf { it.second.height } + caption + pad
        }
        sheet.captureRoboImage("build/shots/widget/contact_sheet.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xhdpi")
    fun `shots in light mode`() {
        shoot("light_row", playing.copy(dark = false), row)
        shoot("light_card", playing.copy(dark = false), card)
        shoot("light_hero", playing.copy(dark = false), hyperOs)
    }

    @Test
    @Config(sdk = [30])
    fun `shots before Android 12`() {
        shoot("api30_row", playing, row)
        shoot("api30_card_armed", playing, card, armed = true)
        shoot("api30_light_card", playing.copy(dark = false), card)
        val image = context.getDrawable(R.drawable.widget_preview_image)!!
        val bitmap = Bitmap.createBitmap(image.intrinsicWidth, image.intrinsicHeight, Bitmap.Config.ARGB_8888)
        image.setBounds(0, 0, bitmap.width, bitmap.height)
        image.draw(Canvas(bitmap))
        bitmap.captureRoboImage("build/shots/widget/picker_image.png")
    }

    @Test
    fun `picker preview`() {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_preview, FrameLayout(context), false)
        draw(view, card).captureRoboImage("build/shots/widget/picker_layout.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-notnight-xhdpi")
    fun `picker preview by day`() {
        val view = LayoutInflater.from(context).inflate(R.layout.widget_preview, FrameLayout(context), false)
        draw(view, card).captureRoboImage("build/shots/widget/picker_layout_light.png")
    }

    private fun draw(view: View, size: DpSize): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val margin = (16 * density).toInt()
        val bitmap = Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF5A6070.toInt())
        canvas.translate(margin.toFloat(), margin.toFloat())
        view.draw(canvas)
        return bitmap
    }
}
