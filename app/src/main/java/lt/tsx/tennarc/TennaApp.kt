package lt.tsx.tennarc

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import java.io.File
import java.util.Locale
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.JustWatch
import lt.tsx.tennarc.link.LocalMedia
import lt.tsx.tennarc.icons.PosterLoader
import lt.tsx.tennarc.link.TitleFinder
import lt.tsx.tennarc.link.Wikidata
import lt.tsx.tennarc.link.YouTubeVideos
import lt.tsx.tennarc.tv.DlnaClient
import lt.tsx.tennarc.icons.StoreIconLoader
import lt.tsx.tennarc.tv.TvDiscovery
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.tv.TvHub
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.androidtv.AndroidTvBackend
import lt.tsx.tennarc.tv.androidtv.AndroidTvFinder
import lt.tsx.tennarc.tv.androidtv.NsdAdbPorts
import lt.tsx.tennarc.tv.firetv.AdbKeys
import lt.tsx.tennarc.tv.firetv.DadbShell
import lt.tsx.tennarc.tv.firetv.FireTvBackend
import lt.tsx.tennarc.tv.firetv.FireTvFinder
import lt.tsx.tennarc.tv.roku.RokuBackend
import lt.tsx.tennarc.tv.roku.RokuFinder
import lt.tsx.tennarc.tv.samsung.SamsungBackend
import lt.tsx.tennarc.tv.samsung.SamsungFinder
import lt.tsx.tennarc.tv.webos.WebOsBackend
import lt.tsx.tennarc.tv.webos.WebOsFinder
import lt.tsx.tennarc.widget.TvWidgets

class TennaApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var controller: TvHub
        private set
    lateinit var posters: PosterLoader
        private set
    lateinit var media: LocalMedia
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
        settings = Settings(this)
        SleepTimer.forgetIfLost(this)
        val web = TvHttp.webClient()
        posters = PosterLoader(web)
        val lg = WebOsFinder(DlnaClient(TvHttp.lanClient()))
        val justWatch = JustWatch(web, ::country)
        media = LocalMedia(this) { controller }
        val adbKeys = AdbKeys(File(filesDir, "adb"))
        controller = TvHub(
            settings,
            TvDiscovery(listOf(lg::find, RokuFinder::find, { SamsungFinder.find() }, { AndroidTvFinder.find(this) }, FireTvFinder(this)::find)),
            TitleFinder(listOf(justWatch, Wikidata(web)), YouTubeVideos(web, ::country), justWatch::picks),
            iconDir = File(noBackupFilesDir, "icons"),
            appsDir = File(noBackupFilesDir, "tv-apps"),
            storeIcons = StoreIconLoader(web),
            phoneFiles = media::serve,
        ) { kind, settings ->
            when (kind) {
                TvKind.WEBOS -> WebOsBackend(settings)
                TvKind.ROKU -> RokuBackend(settings)
                TvKind.ANDROID_TV -> AndroidTvBackend(
                    settings,
                    listsDir = File(noBackupFilesDir, "tv-adb"),
                    adbShells = { host, port -> DadbShell(host, adbKeys, port) },
                    adbPorts = NsdAdbPorts(this),
                )
                TvKind.SAMSUNG -> SamsungBackend(settings)
                TvKind.FIRE_TV -> FireTvBackend(settings, filesDir, keys = adbKeys)
            }
        }
        TvWidgets.follow(this)
    }

    private fun country(): String {
        val phone = getSystemService(TelephonyManager::class.java)
        return listOf(phone?.networkCountryIso, phone?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it != null && it.length == 2 }
            ?.uppercase(Locale.ROOT)
            ?: "US"
    }
}

val Context.tenna: TennaApp get() = applicationContext as TennaApp
