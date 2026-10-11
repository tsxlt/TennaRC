package lt.tsx.tennarc.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import lt.tsx.tennarc.R
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState

fun mirrorApp(apps: List<TvApp>): TvApp? =
    apps.firstOrNull { it.id == LG_SCREEN_SHARE || it.title.equals("Screen Share", ignoreCase = true) }

private fun mirrorReceiver(state: TvState): String? = mirrorApp(state.apps)?.id ?: LG_SCREEN_SHARE.takeIf { state.kind == TvKind.WEBOS }

private const val LG_SCREEN_SHARE = "com.webos.app.miracast"

private fun castSettings(context: Context): Intent? =
    listOf(Settings.ACTION_CAST_SETTINGS, "android.settings.WIFI_DISPLAY_SETTINGS")
        .map { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { it.resolveActivity(context.packageManager) != null }

@Composable
fun rememberPhoneSources(state: TvState, actions: RemoteActions): List<PhoneSource> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cast = remember(context) { castSettings(context) }
    val pick = rememberPhonePicker { file -> scope.launch { actions.open(file, LinkAction.PLAY_MEDIA) } }
    val receiver = mirrorReceiver(state)
    val gallery = LinkAction.PLAY_MEDIA in actions.actionsFor(PHONE_FILE)
    val mirrorName = stringResource(R.string.phone_mirror)
    val mirrorLabel = stringResource(R.string.phone_mirror_label)
    val galleryName = stringResource(R.string.phone_gallery)
    val galleryLabel = stringResource(R.string.phone_gallery_label)
    return buildList {
        if (cast != null) {
            add(
                PhoneSource("phone:mirror", mirrorName, mirrorLabel, Lucide.ScreenShare, active = receiver != null && state.foregroundAppId == receiver) {
                    receiver?.let(actions::launch)
                    try {
                        context.startActivity(cast)
                    } catch (_: ActivityNotFoundException) {
                    } catch (_: SecurityException) {
                    }
                },
            )
        }
        if (gallery) add(PhoneSource("phone:gallery", galleryName, galleryLabel, Lucide.Images, active = false, onClick = pick))
    }
}
