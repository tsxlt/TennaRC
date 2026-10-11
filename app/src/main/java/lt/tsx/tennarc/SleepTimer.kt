package lt.tsx.tennarc

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.tv.TvPower

object SleepTimer {
    fun start(context: Context, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        context.tenna.settings.update { it.copy(sleepAt = at, sleepMinutes = minutes) }
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context))
        }
    }

    fun cancel(context: Context) {
        val alarm = alarm(context)
        context.getSystemService(AlarmManager::class.java).cancel(alarm)
        alarm.cancel()
        context.tenna.settings.update { it.copy(sleepAt = 0) }
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SleepTimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun forgetIfLost(context: Context) {
        val at = context.tenna.settings.prefs.value.sleepAt
        if (at == 0L) return
        val armed = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SleepTimerReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) != null
        if (at <= System.currentTimeMillis() || !armed) context.tenna.settings.update { it.copy(sleepAt = 0) }
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.tenna
        val done = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val tv = app.controller
            tv.hold()
            try {
                val connected = withTimeoutOrNull(CONNECT_MS) { tv.state.first { it.isConnected } }
                val state = connected?.let { withTimeoutOrNull(POWER_MS) { tv.state.first { it.power != null } } ?: tv.state.value }
                if (state != null && state.isConnected && state.power != TvPower.STANDBY) {
                    tv.powerOff()
                    delay(SEND_MS)
                } else {
                    Log.i(TAG, "sleep timer: the TV is off or out of reach already")
                }
            } finally {
                tv.release()
                app.settings.update { if (it.sleepAt <= System.currentTimeMillis()) it.copy(sleepAt = 0) else it }
                done.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Tenna"
        const val CONNECT_MS = 6_000L
        const val POWER_MS = 1_500L
        const val SEND_MS = 1_500L
    }
}
