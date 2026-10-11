package lt.tsx.tennarc

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat.MediaStyle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvState

class RemoteService : Service() {
    private val app get() = tenna
    private val controller get() = app.controller
    private val scope = MainScope()
    private lateinit var session: MediaSessionCompat
    private val icons = mutableMapOf<String, Bitmap?>()
    private var shown: NowPlaying? = null

    private val volume = object : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, 100, 0) {
        override fun onSetVolumeTo(volume: Int) {
            currentVolume = volume
            controller.setVolume(volume)
        }

        override fun onAdjustVolume(direction: Int) {
            when {
                direction > 0 -> controller.volumeUp()
                direction < 0 -> controller.volumeDown()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notify_channel))
                .setDescription(getString(R.string.notify_channel_body))
                .setShowBadge(false)
                .build(),
        )
        session = MediaSessionCompat(this, "Tenna").apply {
            setCallback(Callbacks())
            setSessionActivity(openApp())
            setPlaybackToRemote(volume)
            isActive = true
        }
        val first = nowPlaying(controller.state.value)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            build(first, icon = null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        controller.hold()

        scope.launch {
            combine(controller.state, app.settings.prefs) { state, _ -> state }.collect(::render)
        }
        scope.launch {
            combine(app.settings.prefs.map { it.notificationControls }, app.media.serving) { controls, serving -> controls || serving }
                .distinctUntilChanged()
                .collect { if (!it) stopSelf() }
        }
        scope.launch {
            combine(controller.state.map { it.connection == Connection.Connected && it.power != TvPower.STANDBY }, app.media.serving) { live, serving -> live || serving }
                .distinctUntilChanged()
                .collectLatest { live ->
                    if (!live) {
                        delay(GONE_GRACE_MS)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> controller.playPause()
            ACTION_REWIND -> rewind()
            ACTION_FAST_FORWARD -> fastForward()
            ACTION_CLOSE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        controller.release()
        super.onDestroy()
    }

    private fun nowPlaying(state: TvState) =
        nowPlaying(state, idle = getString(R.string.notify_channel), onTv = getString(R.string.notify_on_tv))

    private suspend fun render(state: TvState) {
        state.volume?.let { volume.currentVolume = it }
        val now = nowPlaying(state)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, now.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, now.subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, now.durationMs ?: -1L)
                .apply { iconFor(state, now)?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it) } }
                .build(),
        )
        session.setPlaybackState(playbackState(now))
        if (now == shown) return
        shown = now
        notify(build(now, iconFor(state, now)))
    }

    private fun playbackState(now: NowPlaying): PlaybackStateCompat {
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD
        if (now.canSeek) actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
        return PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (now.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                now.positionMs ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (now.playing && now.positionMs != null) 1f else 0f,
            )
            .addCustomAction(CUSTOM_REWIND, getString(R.string.key_rewind), android.R.drawable.ic_media_rew)
            .addCustomAction(CUSTOM_FAST_FORWARD, getString(R.string.key_fast_forward), android.R.drawable.ic_media_ff)
            .build()
    }

    private fun build(now: NowPlaying, icon: Bitmap?): Notification {
        val playPause = if (now.playing) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.key_pause), command(ACTION_PLAY_PAUSE))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.key_play), command(ACTION_PLAY_PAUSE))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(now.title)
            .setContentText(now.subtitle)
            .setLargeIcon(icon)
            .setContentIntent(openApp())
            .setDeleteIntent(command(ACTION_CLOSE))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_rew, getString(R.string.key_rewind), command(ACTION_REWIND))
            .addAction(playPause)
            .addAction(android.R.drawable.ic_media_ff, getString(R.string.key_fast_forward), command(ACTION_FAST_FORWARD))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notify_close), command(ACTION_CLOSE))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun notify(notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private suspend fun iconFor(state: TvState, now: NowPlaying): Bitmap? {
        val url = state.apps.firstOrNull { it.id == now.appId }?.iconUrl ?: return null
        return icons.getOrPut(url) { catching { controller.iconLoader.load(url)?.asAndroidBitmap() }.logged("RemoteService.iconFor").getOrNull() }
    }

    private fun rewind() {
        val cast = controller.state.value.cast
        if (cast != null) controller.castSeek((cast.positionSeconds - SKIP_SECONDS).coerceAtLeast(0)) else controller.press(TvButton.REWIND)
    }

    private fun fastForward() {
        val cast = controller.state.value.cast
        if (cast != null) controller.castSeek(cast.positionSeconds + SKIP_SECONDS) else controller.press(TvButton.FAST_FORWARD)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, RemoteService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private inner class Callbacks : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (!controller.state.value.playing || controller.state.value.cast != null) controller.playPause()
        }

        override fun onPause() {
            if (controller.state.value.playing || controller.state.value.cast != null) controller.playPause()
        }

        override fun onRewind() = rewind()

        override fun onFastForward() = fastForward()

        override fun onSeekTo(pos: Long) = controller.castSeek((pos / 1_000).toInt())

        override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
            when (action) {
                CUSTOM_REWIND -> rewind()
                CUSTOM_FAST_FORWARD -> fastForward()
            }
        }
    }

    companion object {
        private const val CHANNEL = "remote"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY_PAUSE = "lt.tsx.tennarc.PLAY_PAUSE"
        private const val ACTION_REWIND = "lt.tsx.tennarc.REWIND"
        private const val ACTION_FAST_FORWARD = "lt.tsx.tennarc.FAST_FORWARD"
        private const val ACTION_CLOSE = "lt.tsx.tennarc.CLOSE_CONTROLS"
        private const val CUSTOM_REWIND = "rewind"
        private const val CUSTOM_FAST_FORWARD = "fast_forward"
        private const val GONE_GRACE_MS = 60_000L
        private const val SKIP_SECONDS = 10

        fun start(context: Context) {
            catching { ContextCompat.startForegroundService(context, Intent(context, RemoteService::class.java)) }.logged("RemoteService.start")
        }
    }
}
