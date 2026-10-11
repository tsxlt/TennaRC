package lt.tsx.tennarc.link

enum class StreamingService(
    val label: String,
    val justWatch: Set<String>,
    val domains: Set<String>,
    val justWatchCode: String? = null,
    val appIds: Set<String> = emptySet(),
) {
    NETFLIX(
        label = "Netflix",
        justWatch = setOf("netflix", "netflixkids", "netflixbasicwithads"),
        domains = setOf("netflix.com"),
        justWatchCode = "nfx",
        appIds = setOf("netflix", "com.netflix.ninja", "12", "3201907018807", "11101200001"),
    ),
    YOUTUBE(
        label = "YouTube",
        justWatch = setOf("youtube", "youtubepremium", "youtubefree"),
        domains = setOf("youtube.com", "youtu.be", "music.youtube.com", "youtube-nocookie.com"),
        appIds = setOf("youtube.leanback.v4", "com.google.android.youtube.tv", "com.amazon.firetv.youtube", "837", "111299001912"),
    ),
    PRIME_VIDEO(
        label = "Prime Video",
        justWatch = setOf("amazonprimevideo", "amazonprime", "amazonprimevideowithads"),
        domains = setOf("primevideo.com", "app.primevideo.com", "watch.amazon.com"),
        justWatchCode = "prv",
        appIds = setOf("amazon", "com.amazon.amazonvideo.livingroom", "com.amazon.avod", "13", "3201910019365"),
    ),
    DISNEY_PLUS(
        label = "Disney+",
        justWatch = setOf("disneyplus"),
        domains = setOf("disneyplus.com"),
        justWatchCode = "dnp",
        appIds = setOf("com.disney.disneyplus-prod", "com.disney.disneyplus", "291097", "3201901017640"),
    ),
    MAX(
        label = "HBO Max",
        justWatch = setOf("max", "hbomax"),
        domains = setOf("max.com", "play.max.com", "hbomax.com", "play.hbomax.com"),
        justWatchCode = "mxx",
        appIds = setOf("com.wbd.stream", "com.hbo.hbomax", "com.hbo.hbomax-2017", "61322", "3202301029760"),
    ),
    APPLE_TV(
        label = "Apple TV+",
        justWatch = setOf("appletvplus"),
        domains = setOf("tv.apple.com"),
        justWatchCode = "atp",
        appIds = setOf("com.apple.appletv", "com.apple.atve.androidtv.appletv", "551012", "3201807016597"),
    ),
    GO3(
        label = "Go3",
        justWatch = setOf("go3"),
        domains = setOf("go3.lt", "go3.lv", "go3.ee", "go3.tv"),
        justWatchCode = "gth",
        appIds = setOf("lt.go3.lg.tv"),
    ),
    TELIA_PLAY(
        label = "Telia Play",
        justWatch = setOf("teliaplay"),
        domains = setOf("teliaplay.lt", "teliaplay.se", "teliaplay.fi", "teliaplay.ee"),
        justWatchCode = "tlp",
    ),
    SKYSHOWTIME(
        label = "SkyShowtime",
        justWatch = setOf("skyshowtime"),
        domains = setOf("skyshowtime.com"),
    ),
    CRUNCHYROLL(
        label = "Crunchyroll",
        justWatch = setOf("crunchyroll"),
        domains = setOf("crunchyroll.com"),
        justWatchCode = "cru",
        appIds = setOf("com.crunchyroll.crunchyroid", "2595"),
    ),
    MUBI(
        label = "MUBI",
        justWatch = setOf("mubi"),
        domains = setOf("mubi.com"),
        justWatchCode = "mbi",
        appIds = setOf("com.mubi"),
    ),
    PLEX(
        label = "Plex",
        justWatch = setOf("plex"),
        domains = setOf("plex.tv", "app.plex.tv", "watch.plex.tv"),
        justWatchCode = "plx",
        appIds = setOf("cdp-30", "cdmnwpxrqb.plex", "com.plexapp.android", "13535"),
    ),
    ;

    val sampleLink: LinkTarget
        get() = when (this) {
            NETFLIX -> LinkTarget.Netflix("https://www.netflix.com/title/0", "0")
            YOUTUBE -> LinkTarget.YouTube("https://www.youtube.com/watch?v=00000000000", "00000000000")
            PRIME_VIDEO -> LinkTarget.Streaming("https://app.primevideo.com/detail?gti=amzn1.dv.gti.00000000-0000-0000-0000-000000000000", this)
            APPLE_TV -> LinkTarget.Streaming("https://tv.apple.com/us/movie/sample/umc.cmc.0", this)
            else -> LinkTarget.Streaming("https://${domains.first()}/", this)
        }

    companion object {
        fun byJustWatch(technicalName: String): StreamingService? = entries.firstOrNull { technicalName in it.justWatch }

        fun byAppId(appId: String): StreamingService? = entries.firstOrNull { appId in it.appIds }

        fun byHost(host: String): StreamingService? = entries.firstOrNull { service -> service.domains.any { host == it || host.endsWith(".$it") } }
    }
}
