package lt.tsx.tennarc.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import lt.tsx.tennarc.MainActivity
import lt.tsx.tennarc.R
import lt.tsx.tennarc.tenna
import lt.tsx.tennarc.tv.TvApps

class TvWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(PREVIEW_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.tenna
        val models = TvWidgets.models(app)
        val art = WidgetArt(app.controller.iconLoader, context.resources.displayMetrics.density)
        provideContent {
            val model by models.collectAsState()
            val now = System.currentTimeMillis()
            TvWidgetContent(
                model = model,
                size = LocalSize.current,
                armed = now < (currentState(ARMED_UNTIL) ?: 0L),
                waking = now < (currentState(WAKING_UNTIL) ?: 0L),
                art = art,
            )
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val app = context.tenna
        val model = previewModel(TvWidgets.models(app).value)
        val art = WidgetArt(app.controller.iconLoader, context.resources.displayMetrics.density)
        provideContent { TvWidgetContent(model, LocalSize.current, armed = false, waking = false, art = art) }
    }

    internal companion object {
        val ARMED_UNTIL: Preferences.Key<Long> = longPreferencesKey("armed_until")

        val WAKING_UNTIL: Preferences.Key<Long> = longPreferencesKey("waking_until")

        val PREVIEW_SIZES = setOf(DpSize(176.dp, 92.dp), DpSize(176.dp, 196.dp), DpSize(338.dp, 92.dp), DpSize(338.dp, 196.dp))
    }
}

class TvWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TvWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.cancel(context)
    }
}

internal fun previewModel(live: WidgetModel): WidgetModel {
    if (live.mode == WidgetMode.READY && live.title != null) return live
    val youtube = live.favorites.firstOrNull { it.id == TvApps.YOUTUBE } ?: WidgetApp(TvApps.YOUTUBE, "YouTube")
    return live.copy(
        mode = WidgetMode.READY,
        title = youtube.title,
        playing = true,
        muted = false,
        app = youtube,
        casting = false,
        favorites = live.favorites.filterNot { it.id == youtube.id },
    )
}

private val EDGE = 12.dp

private val PAIR_GAP = 8.dp

private val ART_GAP = 12.dp
private val GLYPH = 24.dp

private val PILL = 104.dp
private val PILL_MAX = 120.dp
private val DIVIDER = 20.dp

private val TEXT_MIN = 64.dp

@Composable
internal fun TvWidgetContent(model: WidgetModel, size: DpSize, armed: Boolean, waking: Boolean, art: WidgetArt) {
    CompositionLocalProvider(LocalWidgetPalette provides WidgetPalette.of(LocalContext.current, model.dark), LocalWidgetArt provides art) {
        Widget(model, size, armed, waking)
    }
}

@Composable
private fun Widget(model: WidgetModel, size: DpSize, armed: Boolean, waking: Boolean) {
    val context = LocalContext.current
    val palette = LocalWidgetPalette.current
    val layout = widgetLayout(size)
    val line = widgetLine(model, armed, waking)
    val backdrop = WidgetFrost.backdrop(size, context.resources.displayMetrics.density, widgetRadius(context), palette)
    var root = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(backdrop))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) root = root.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    val openApp = actionStartActivity<MainActivity>()
    val ready = model.mode == WidgetMode.READY
    val glyph = if (size.width >= WIDE) KEY else null
    when {
        model.mode == WidgetMode.SETUP || (!ready && !model.canWake) -> Row(
            root.padding(horizontal = EDGE).clickable(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Identity(model, line, glyph, GlanceModifier.defaultWeight()) }

        !ready -> if (layout == WidgetLayout.COMPACT) {
            Box(root.clickable(openApp), contentAlignment = Alignment.Center) { PowerKey(model, armed) }
        } else {
            Row(root.padding(horizontal = EDGE), verticalAlignment = Alignment.CenterVertically) {
                Identity(model, line, glyph, GlanceModifier.defaultWeight().clickable(openApp))
                PowerKey(model, armed)
            }
        }

        layout == WidgetLayout.COMPACT -> Compact(root.clickable(openApp), size, model)
        layout == WidgetLayout.ROW -> WideRow(root, size, model, line, openApp)
        else -> Tall(root, size, layout, model, line, armed, openApp)
    }
}

@Composable
private fun Compact(root: GlanceModifier, size: DpSize, model: WidgetModel) {
    val inner = size.width - PAIR_GAP * 2
    val pill = (inner - KEY - PAIR_GAP).coerceIn(80.dp, PILL_MAX)
    Row(root, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        PlayPauseKey(model)
        Spacer(GlanceModifier.width(PAIR_GAP))
        VolumePill(pill)
    }
}

@Composable
private fun WideRow(root: GlanceModifier, size: DpSize, model: WidgetModel, line: WidgetLine, openApp: Action) {
    val room = size.width - EDGE * 2 - KEY - PAIR_GAP - PILL - ART_GAP
    val text = room >= TEXT_MIN
    val art = room >= KEY && (!text || room >= TEXT_MIN + KEY + ART_GAP)
    Row(root.padding(horizontal = EDGE), verticalAlignment = Alignment.CenterVertically) {
        when {
            text -> Identity(model, line, if (art) KEY else null, GlanceModifier.defaultWeight().clickable(openApp))
            art -> Row(GlanceModifier.defaultWeight().clickable(openApp)) { Art(model, KEY) }
            else -> Spacer(GlanceModifier.defaultWeight())
        }
        if (text || art) Spacer(GlanceModifier.width(ART_GAP))
        PlayPauseKey(model)
        Spacer(GlanceModifier.width(PAIR_GAP))
        VolumePill(PILL)
        if (!text && !art) Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Tall(root: GlanceModifier, size: DpSize, layout: WidgetLayout, model: WidgetModel, line: WidgetLine, armed: Boolean, openApp: Action) {
    val square = layout == WidgetLayout.SQUARE
    val inner = size.width - EDGE * 2
    val columns = if (square) 2 else widgetColumns(inner)
    val gap = if (square) PAIR_GAP else (inner - KEY * columns) / (columns - 1)
    val stack = widgetStack(layout, size, model, columns)
    val together = stack.gap != null
    Column(root.padding(horizontal = EDGE, vertical = if (together) MAX_MARGIN else stack.margin)) {
        if (together) Spacer(GlanceModifier.defaultWeight())
        when {
            !stack.art -> Row(GlanceModifier.fillMaxWidth().height(KEY), verticalAlignment = Alignment.CenterVertically) {
                Identity(model, line, if (square) null else KEY, GlanceModifier.defaultWeight().clickable(openApp))
                PowerKey(model, armed)
            }
            square -> {
                Row(GlanceModifier.fillMaxWidth().height(KEY), verticalAlignment = Alignment.CenterVertically) {
                    Name(model, line, GlanceModifier.defaultWeight().height(KEY).clickable(openApp))
                    PowerKey(model, armed)
                }
                Gap(stack)
                Row(GlanceModifier.fillMaxWidth().height(ART).clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
                    NowPlaying(model, line)
                }
            }
            else -> {
                Row(GlanceModifier.fillMaxWidth().height(HEADER), verticalAlignment = Alignment.CenterVertically) {
                    Name(model, line, GlanceModifier.defaultWeight().height(HEADER).clickable(openApp))
                    if (stack.chip) model.nextTv?.let { NextTvChip(it) }
                }
                Gap(stack)
                Row(GlanceModifier.fillMaxWidth().height(ART), verticalAlignment = Alignment.CenterVertically) {
                    Row(GlanceModifier.defaultWeight().height(ART).clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
                        NowPlaying(model, line)
                    }
                    PowerKey(model, armed)
                }
            }
        }
        Gap(stack)
        if (stack.favorites > 0) {
            Favorites(model.favorites.take(stack.favorites), gap)
            Gap(stack)
        }
        Row(GlanceModifier.fillMaxWidth().height(KEY), verticalAlignment = Alignment.CenterVertically) {
            if (columns == 4) {
                MuteKey(model)
                Spacer(GlanceModifier.width(gap))
            }
            PlayPauseKey(model)
            Spacer(GlanceModifier.defaultWeight())
            VolumePill(if (square) (inner - KEY - PAIR_GAP).coerceAtMost(PILL_MAX) else KEY * 2 + gap)
        }
        if (together) Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Name(model: WidgetModel, line: WidgetLine, modifier: GlanceModifier) {
    val palette = LocalWidgetPalette.current
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (line != WidgetLine.NONE) {
            Text(model.tvName, style = textStyle(palette.inkDim, 13.sp), maxLines = 1, modifier = GlanceModifier.padding(start = NAME_INDENT))
        }
    }
}

@Composable
private fun RowScope.NowPlaying(model: WidgetModel, line: WidgetLine) {
    Art(model, ART)
    Spacer(GlanceModifier.width(ART_GAP))
    Headline(model, line, GlanceModifier.defaultWeight(), maxLines = 2, size = 17.sp)
}

private val NAME_INDENT = 2.dp

@Composable
private fun ColumnScope.Gap(stack: WidgetStack) =
    Spacer(stack.gap?.let { GlanceModifier.height(it) } ?: GlanceModifier.defaultWeight())

@Composable
private fun Identity(model: WidgetModel, line: WidgetLine, art: Dp?, modifier: GlanceModifier) {
    Row(modifier.height(KEY), verticalAlignment = Alignment.CenterVertically) {
        if (art != null) {
            Art(model, art)
            Spacer(GlanceModifier.width(ART_GAP))
        }
        Label(model, line, GlanceModifier.defaultWeight(), indent = art == null)
    }
}

@Composable
private fun Art(model: WidgetModel, size: Dp) {
    val palette = LocalWidgetPalette.current
    Image(ImageProvider(LocalWidgetArt.current.now(model, size, palette)), contentDescription = null, modifier = GlanceModifier.size(size))
}

@Composable
private fun status(model: WidgetModel, line: WidgetLine): String? {
    val context = LocalContext.current
    return when (line) {
        WidgetLine.ARMED -> context.getString(R.string.power_off_confirm)
        WidgetLine.WAKING -> context.getString(R.string.notice_wake_sent)
        WidgetLine.OFF -> context.getString(R.string.offline_title)
        WidgetLine.SETUP -> context.getString(R.string.status_not_paired)
        WidgetLine.TITLE -> model.title.orEmpty()
        WidgetLine.NONE -> null
    }
}

@Composable
private fun Label(model: WidgetModel, line: WidgetLine, modifier: GlanceModifier, indent: Boolean = false) {
    val palette = LocalWidgetPalette.current
    val second = status(model, line)
    Column(if (indent) modifier.padding(horizontal = EDGE) else modifier, verticalAlignment = Alignment.CenterVertically) {
        if (second == null) {
            Text(model.tvName, style = textStyle(palette.ink, 15.sp), maxLines = 1)
            return@Column
        }
        Text(model.tvName, style = textStyle(palette.inkDim, 12.sp), maxLines = 1)
        Headline(model, line, GlanceModifier, maxLines = 1)
    }
}

@Composable
private fun Headline(model: WidgetModel, line: WidgetLine, modifier: GlanceModifier, maxLines: Int, size: TextUnit = 15.sp) {
    val palette = LocalWidgetPalette.current
    val text = status(model, line) ?: model.tvName
    val color = when (line) {
        WidgetLine.ARMED -> palette.danger
        WidgetLine.OFF, WidgetLine.WAKING, WidgetLine.SETUP -> palette.inkDim
        else -> palette.ink
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = textStyle(color, size), maxLines = maxLines)
        if (line == WidgetLine.TITLE && model.playing) {
            Spacer(GlanceModifier.width(8.dp))
            Image(
                ImageProvider(R.drawable.widget_dot),
                contentDescription = null,
                modifier = GlanceModifier.size(6.dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.accent)),
            )
        }
    }
}

private fun textStyle(color: Color, size: TextUnit) = TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = FontWeight.Medium)

@Composable
private fun Favorites(apps: List<WidgetApp>, gap: Dp) {
    val palette = LocalWidgetPalette.current
    val art = LocalWidgetArt.current
    Row(GlanceModifier.fillMaxWidth().height(KEY), verticalAlignment = Alignment.CenterVertically) {
        apps.forEachIndexed { i, app ->
            if (i > 0) Spacer(GlanceModifier.width(gap))
            Image(
                ImageProvider(art.app(app, KEY, palette)),
                contentDescription = app.title,
                modifier = GlanceModifier
                    .size(KEY)
                    .clickable(actionRunCallback<WidgetLaunchAppAction>(actionParametersOf(APP_PARAM to app.id)), rippleOverride = R.drawable.widget_ripple),
            )
        }
    }
}

@Composable
private fun NextTvChip(next: NextTv) {
    val context = LocalContext.current
    val palette = LocalWidgetPalette.current
    val name = if (next.demo) context.getString(R.string.settings_demo_tv) else next.name
    val label = context.getString(R.string.offline_switch, name)
    Row(
        GlanceModifier
            .height(HEADER)
            .background(ImageProvider(R.drawable.widget_volume_pill), colorFilter = ColorFilter.tint(ColorProvider(palette.key)))
            .clickable(actionRunCallback<WidgetSwitchTvAction>(), rippleOverride = R.drawable.widget_ripple_pill)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(R.drawable.widget_switch), contentDescription = null, modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(palette.inkDimProvider))
        Spacer(GlanceModifier.width(8.dp))
        Text(name, style = textStyle(palette.ink, 13.sp), maxLines = 1)
    }
}

@Composable
private fun PlayPauseKey(model: WidgetModel) = Key(
    icon = if (model.playing) R.drawable.widget_pause else R.drawable.widget_play,
    description = if (model.playing) R.string.key_pause else R.string.key_play,
    key = WidgetKey.PLAY_PAUSE,
    fill = LocalWidgetPalette.current.key,
)

@Composable
private fun MuteKey(model: WidgetModel) = Key(
    icon = if (model.muted) R.drawable.widget_volume_off else R.drawable.widget_volume,
    description = if (model.muted) R.string.key_unmute else R.string.key_mute,
    key = WidgetKey.MUTE,
    fill = LocalWidgetPalette.current.key,
)

@Composable
private fun PowerKey(model: WidgetModel, armed: Boolean) {
    val palette = LocalWidgetPalette.current
    val off = model.mode == WidgetMode.OFF
    Key(
        icon = R.drawable.widget_power,
        description = when {
            off -> R.string.action_turn_on
            armed -> R.string.power_off_confirm
            else -> R.string.power_turn_off
        },
        key = WidgetKey.POWER,
        fill = when {
            off -> palette.key
            armed -> palette.dangerContainer
            else -> null
        },
        ink = if (armed && !off) palette.danger else palette.ink,
    )
}

@Composable
private fun Key(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    key: WidgetKey,
    fill: Color? = null,
    ink: Color = LocalWidgetPalette.current.ink,
) {
    val label = LocalContext.current.getString(description)
    var modifier = GlanceModifier.size(KEY)
    if (fill != null) {
        modifier = modifier.background(ImageProvider(R.drawable.widget_key), colorFilter = ColorFilter.tint(ColorProvider(fill)))
    }
    Box(
        modifier
            .clickable(actionRunCallback<WidgetKeyAction>(actionParametersOf(KEY_PARAM to key.name)), rippleOverride = R.drawable.widget_ripple)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(GLYPH), colorFilter = ColorFilter.tint(ColorProvider(ink)))
    }
}

@Composable
private fun VolumePill(width: Dp) {
    val palette = LocalWidgetPalette.current
    Row(
        GlanceModifier
            .width(width)
            .height(KEY)
            .background(ImageProvider(R.drawable.widget_volume_pill), colorFilter = ColorFilter.tint(ColorProvider(palette.key))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillEnd(R.drawable.widget_minus, R.string.key_volume_down, WidgetKey.VOLUME_DOWN)
        Box(GlanceModifier.width(1.dp).height(DIVIDER).background(palette.hairline)) {}
        PillEnd(R.drawable.widget_plus, R.string.key_volume_up, WidgetKey.VOLUME_UP)
    }
}

@Composable
private fun RowScope.PillEnd(@DrawableRes icon: Int, @StringRes description: Int, key: WidgetKey) {
    val palette = LocalWidgetPalette.current
    val label = LocalContext.current.getString(description)
    Box(
        GlanceModifier
            .defaultWeight()
            .height(KEY)
            .clickable(actionRunCallback<WidgetKeyAction>(actionParametersOf(KEY_PARAM to key.name)), rippleOverride = R.drawable.widget_ripple_pill)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(GLYPH), colorFilter = ColorFilter.tint(palette.inkProvider))
    }
}

internal val KEY_PARAM = ActionParameters.Key<String>("key")

internal val APP_PARAM = ActionParameters.Key<String>("app")
