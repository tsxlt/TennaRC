package lt.tsx.tennarc.ui.onboarding

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.currentTv
import lt.tsx.tennarc.data.forgetTv
import lt.tsx.tennarc.data.switchTo
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvKind

internal enum class PairingStyle {
    APPROVE,

    CODE,

    NONE,
}

internal val TvKind.pairingStyle: PairingStyle
    get() = when (this) {
        TvKind.WEBOS, TvKind.SAMSUNG, TvKind.FIRE_TV -> PairingStyle.APPROVE
        TvKind.ANDROID_TV -> PairingStyle.CODE
        TvKind.ROKU -> PairingStyle.NONE
    }

internal enum class SetupHint {
    ROKU_MOBILE_CONTROL,

    FIRE_TV_ADB,
}

internal fun Notice.asHint(kind: TvKind): SetupHint? = when {
    this == Notice.ROKU_LIMITED && kind == TvKind.ROKU -> SetupHint.ROKU_MOBILE_CONTROL
    this == Notice.ADB_OFF && kind == TvKind.FIRE_TV -> SetupHint.FIRE_TV_ADB
    else -> null
}

internal data class Setup(
    val found: List<FoundTv> = emptyList(),
    val wifi: Boolean = true,
    val scanning: Boolean = wifi,
    val scan: Int = 0,
    val picked: FoundTv? = null,
    val asked: Boolean = false,
    val link: Connection? = null,
    val heard: Boolean = false,
    val hint: SetupHint? = null,
    val paired: Boolean = false,
)

internal sealed interface SetupEvent {
    data class Found(val tvs: List<FoundTv>) : SetupEvent
    data object ScanEnded : SetupEvent
    data object Rescan : SetupEvent
    data class Wifi(val on: Boolean) : SetupEvent
    data class Pick(val tv: FoundTv) : SetupEvent
    data object Asked : SetupEvent
    data class Link(val connection: Connection) : SetupEvent
    data class Hint(val hint: SetupHint) : SetupEvent

    data object Confirmed : SetupEvent

    data object Finish : SetupEvent
    data object Retry : SetupEvent
    data object Back : SetupEvent
}

internal fun Setup.reduce(event: SetupEvent): Setup = when (event) {
    is SetupEvent.Found -> copy(found = (found + event.tvs).distinctBy { it.host })
    SetupEvent.ScanEnded -> copy(scanning = false)
    SetupEvent.Rescan -> if (wifi) copy(scanning = true, scan = scan + 1) else this
    is SetupEvent.Wifi -> when {
        event.on == wifi -> this
        !event.on -> copy(wifi = false, scanning = false)
        picked != null -> copy(wifi = true).reduce(SetupEvent.Retry)
        else -> copy(wifi = true, scanning = true, scan = scan + 1)
    }
    is SetupEvent.Pick -> copy(picked = event.tv, scanning = false, asked = false, link = null, heard = false, hint = null, paired = false)
    SetupEvent.Asked -> if (picked != null) copy(asked = true) else this
    is SetupEvent.Link -> if (picked == null || !asked || paired) {
        this
    } else {
        val prompting = event.connection == Connection.AwaitingApproval || event.connection is Connection.AwaitingCode
        copy(
            link = event.connection,
            heard = heard || event.connection !is Connection.NeedsPairing,
            hint = if (prompting) null else hint,
        )
    }
    is SetupEvent.Hint -> if (picked != null && !paired) copy(hint = event.hint) else this
    SetupEvent.Confirmed -> if (picked != null && link == Connection.Connected && hint == null) copy(paired = true) else this
    SetupEvent.Finish -> if (picked != null) copy(paired = true) else this
    SetupEvent.Retry -> if (picked != null && !paired) copy(asked = false, link = null, heard = false, hint = null) else this
    SetupEvent.Back -> if (paired) this else copy(picked = null, asked = false, link = null, heard = false, hint = null)
}

internal sealed interface SetupScreen {
    data object NoWifi : SetupScreen

    data class Choose(val tvs: List<FoundTv>, val scanning: Boolean) : SetupScreen
    data object NothingFound : SetupScreen
    data class Pairing(val tv: FoundTv, val step: PairStep) : SetupScreen
}

internal enum class PairStep { CONNECTING, APPROVE, CODE, WRONG_CODE, DECLINED, TIMED_OUT, STOPPED, OFFLINE, ROKU_HINT, ADB_HINT, PAIRED }

internal val Setup.screen: SetupScreen
    get() {
        val tv = picked
        return when {
            tv != null && paired -> SetupScreen.Pairing(tv, PairStep.PAIRED)
            !wifi -> SetupScreen.NoWifi
            tv != null -> SetupScreen.Pairing(tv, step(tv.kind))
            found.isNotEmpty() || scanning -> SetupScreen.Choose(found, scanning)
            else -> SetupScreen.NothingFound
        }
    }

private fun Setup.step(kind: TvKind): PairStep {
    when (hint) {
        SetupHint.ROKU_MOBILE_CONTROL -> return PairStep.ROKU_HINT
        SetupHint.FIRE_TV_ADB -> return PairStep.ADB_HINT
        null -> Unit
    }
    val coming = when (kind.pairingStyle) {
        PairingStyle.APPROVE -> PairStep.APPROVE
        PairingStyle.CODE -> PairStep.CODE
        PairingStyle.NONE -> PairStep.CONNECTING
    }
    return when (val link = link) {
        null, Connection.Connecting, Connection.Connected -> coming
        Connection.AwaitingApproval -> PairStep.APPROVE
        is Connection.AwaitingCode -> if (link.wrong) PairStep.WRONG_CODE else PairStep.CODE
        Connection.Offline -> PairStep.OFFLINE
        is Connection.NeedsPairing -> when {
            !heard -> coming
            link.declined -> PairStep.DECLINED
            link.timedOut -> PairStep.TIMED_OUT
            else -> PairStep.STOPPED
        }
    }
}

internal data class TvLabel(val name: String, val detail: String)

private val LG_FACTORY = Regex("""(?:LG\s+)?webOS TV\s*(.*)""", RegexOption.IGNORE_CASE)

internal val FoundTv.label: TvLabel
    get() {
        val own = name.removePrefix("[LG]").trim()
        val lgModel = LG_FACTORY.matchEntire(own)?.groupValues?.get(1)?.trim()
        val shown = if (own.isEmpty() || lgModel != null) kind.label else own
        val detail = (model ?: lgModel)?.trim()?.takeIf { it.isNotEmpty() && !it.equals(shown, ignoreCase = true) }
        return TvLabel(shown, detail ?: host)
    }

internal fun FoundTv.toSaved(): SavedTv = SavedTv(label.name, host, kind, mac = "")

internal data class TvChoice(val current: SavedTv, val others: List<SavedTv>, val demo: Boolean, val order: List<String> = emptyList())

internal val Prefs.tvChoice: TvChoice get() = TvChoice(currentTv, otherTvs, demoTv, tvOrder)

internal fun Prefs.trying(tv: SavedTv, adding: Boolean): Prefs {
    val chosen = if (adding) switchTo(otherTvs.firstOrNull { it.host == tv.host } ?: tv) else copy(tvName = tv.name, host = tv.host, kind = tv.kind, mac = tv.mac)
    return chosen.copy(demoTv = false)
}

internal fun Prefs.untrying(before: TvChoice): Prefs {
    val tried = currentTv
    val restored = copy(
        tvName = before.current.name,
        host = before.current.host,
        kind = before.current.kind,
        mac = before.current.mac,
        otherTvs = before.others,
        demoTv = before.demo,
        tvOrder = before.order,
    )
    return if (before.others.any { it.host == tried.host }) restored else restored.forgetTv(tried)
}

internal fun Prefs.settled(): Prefs = copy(tvChosen = true, demoTv = false)

internal suspend fun kindAt(host: String, reachable: suspend (String, TvKind) -> Boolean): TvKind? = coroutineScope {
    TvKind.entries.map { kind -> async { kind.takeIf { reachable(host, kind) } } }.awaitAll().firstNotNullOfOrNull { it }
}

private fun FoundTv.flat(): List<String> = listOf(host, name, id.orEmpty(), kind.name, model.orEmpty())

private fun List<String>.foundTvs(): List<FoundTv> = chunked(5) { (host, name, id, kind, model) ->
    FoundTv(host, name, id.ifEmpty { null }, TvKind.valueOf(kind), model.ifEmpty { null })
}

private fun SavedTv.flat(): List<String> = listOf(name, host, kind.name, mac)

private fun List<String>.savedTvs(): List<SavedTv> = chunked(4) { (name, host, kind, mac) -> SavedTv(name, host, TvKind.valueOf(kind), mac) }

internal val SetupSaver: Saver<Setup, Any> = listSaver(
    save = {
        listOf(
            ArrayList(it.found.flatMap { tv -> tv.flat() }),
            it.wifi,
            it.scanning,
            it.scan,
            it.picked?.let { tv -> ArrayList(tv.flat()) },
            it.asked,
            it.heard,
            it.hint?.name,
            it.paired,
        )
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        Setup(
            found = (it[0] as List<String>).foundTvs(),
            wifi = it[1] as Boolean,
            scanning = it[2] as Boolean,
            scan = it[3] as Int,
            picked = (it[4] as List<String>?)?.foundTvs()?.firstOrNull(),
            asked = it[5] as Boolean,
            heard = it[6] as Boolean,
            hint = (it[7] as String?)?.let(SetupHint::valueOf),
            paired = it[8] as Boolean,
        )
    },
)

internal val TvChoiceSaver: Saver<TvChoice, Any> = listSaver(
    save = { listOf(ArrayList(it.current.flat()), ArrayList(it.others.flatMap { tv -> tv.flat() }), it.demo) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        TvChoice((it[0] as List<String>).savedTvs().first(), (it[1] as List<String>).savedTvs(), it[2] as Boolean)
    },
)
