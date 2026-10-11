package lt.tsx.tennarc.ui.onboarding

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.data.tvKey
import lt.tsx.tennarc.data.withPad
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvDiscovery
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFlowTest {
    private val lg = FoundTv("192.168.1.70", "[LG] webOS TV UM7450PLA", kind = TvKind.WEBOS)
    private val samsung = FoundTv("192.168.1.30", "Living Room", kind = TvKind.SAMSUNG, model = "QE55Q80T")
    private val roku = FoundTv("192.168.1.40", "Bedroom", kind = TvKind.ROKU, model = "Roku Ultra")
    private val android = FoundTv("192.168.1.50", "Philips Google TV", kind = TvKind.ANDROID_TV)
    private val fire = FoundTv("192.168.1.60", "Kitchen Fire TV", kind = TvKind.FIRE_TV)

    private fun Setup.after(vararg events: SetupEvent) = events.fold(this) { setup, event -> setup.reduce(event) }

    private fun pairing(tv: FoundTv, vararg events: SetupEvent) =
        Setup().after(SetupEvent.Found(listOf(tv)), SetupEvent.Pick(tv), SetupEvent.Asked, *events)

    private val Setup.step get() = (screen as SetupScreen.Pairing).step

    @Test
    fun `it looks straight away`() {
        assertEquals(SetupScreen.Choose(emptyList(), scanning = true), Setup().screen)
    }

    @Test
    fun `TVs join the list as they answer, in that order, once each`() {
        val setup = Setup().after(SetupEvent.Found(listOf(samsung)), SetupEvent.Found(listOf(samsung, lg)), SetupEvent.Found(listOf(roku, lg)))
        assertEquals(SetupScreen.Choose(listOf(samsung, lg, roku), scanning = true), setup.screen)
        assertEquals(SetupScreen.Choose(listOf(samsung, lg, roku), scanning = false), setup.reduce(SetupEvent.ScanEnded).screen)
    }

    @Test
    fun `nothing found says so, and trying again looks again`() {
        val none = Setup().after(SetupEvent.ScanEnded)
        assertEquals(SetupScreen.NothingFound, none.screen)
        val again = none.reduce(SetupEvent.Rescan)
        assertTrue(again.scanning)
        assertEquals(none.scan + 1, again.scan)
        assertEquals(SetupScreen.Choose(emptyList(), scanning = true), again.screen)
    }

    @Test
    fun `off Wi-Fi it waits, and looks again the moment Wi-Fi is back`() {
        val off = Setup().after(SetupEvent.Wifi(false))
        assertEquals(SetupScreen.NoWifi, off.screen)
        assertFalse(off.scanning)
        val back = off.reduce(SetupEvent.Wifi(true))
        assertTrue(back.scanning)
        assertEquals(off.scan + 1, back.scan)
        assertFalse(Setup(wifi = false).scanning)
        assertEquals(SetupScreen.NoWifi, Setup(wifi = false).screen)
    }

    @Test
    fun `one TV found still waits for a tap`() {
        val setup = Setup().after(SetupEvent.Found(listOf(lg)), SetupEvent.ScanEnded)
        assertEquals(SetupScreen.Choose(listOf(lg), scanning = false), setup.screen)
        assertNull(setup.picked)
    }

    @Test
    fun `picking stops the scan and shows what the TV will ask`() {
        val picked = Setup().after(SetupEvent.Found(listOf(lg)), SetupEvent.Pick(lg))
        assertFalse(picked.scanning)
        assertEquals(SetupScreen.Pairing(lg, PairStep.APPROVE), picked.screen)
        assertEquals(PairStep.CODE, Setup().after(SetupEvent.Pick(android)).step)
        assertEquals(PairStep.CONNECTING, Setup().after(SetupEvent.Pick(roku)).step)
    }

    @Test
    fun `the connection before pairing was asked for isn't the TV's answer`() {
        val early = Setup().after(SetupEvent.Pick(lg), SetupEvent.Link(Connection.NeedsPairing(declined = true)))
        assertNull(early.link)
        assertEquals(PairStep.APPROVE, pairing(lg, SetupEvent.Link(Connection.NeedsPairing())).step)
    }

    @Test
    fun `an approving TV's answers`() {
        assertEquals(PairStep.APPROVE, pairing(lg, SetupEvent.Link(Connection.AwaitingApproval)).step)
        val asked = arrayOf(SetupEvent.Link(Connection.Connecting), SetupEvent.Link(Connection.AwaitingApproval))
        assertEquals(PairStep.DECLINED, pairing(lg, *asked, SetupEvent.Link(Connection.NeedsPairing(declined = true))).step)
        assertEquals(PairStep.TIMED_OUT, pairing(samsung, *asked, SetupEvent.Link(Connection.NeedsPairing(timedOut = true))).step)
        assertEquals(PairStep.STOPPED, pairing(lg, *asked, SetupEvent.Link(Connection.NeedsPairing())).step)
        assertEquals(PairStep.OFFLINE, pairing(lg, SetupEvent.Link(Connection.Offline)).step)
    }

    @Test
    fun `a code that didn't match`() {
        assertEquals(PairStep.CODE, pairing(android, SetupEvent.Link(Connection.AwaitingCode())).step)
        assertEquals(PairStep.WRONG_CODE, pairing(android, SetupEvent.Link(Connection.AwaitingCode(wrong = true))).step)
    }

    @Test
    fun `connected is done once nothing else comes up`() {
        val connected = pairing(lg, SetupEvent.Link(Connection.AwaitingApproval), SetupEvent.Link(Connection.Connected))
        assertEquals(PairStep.APPROVE, connected.step)
        assertFalse(connected.paired)
        val done = connected.reduce(SetupEvent.Confirmed)
        assertTrue(done.paired)
        assertEquals(SetupScreen.Pairing(lg, PairStep.PAIRED), done.screen)
        assertEquals(done, done.after(SetupEvent.Back, SetupEvent.Retry, SetupEvent.Link(Connection.Offline)))
    }

    @Test
    fun `a Roku held back from phones says what to change, and Done goes on anyway`() {
        val limited = pairing(roku, SetupEvent.Link(Connection.Connected), SetupEvent.Hint(SetupHint.ROKU_MOBILE_CONTROL), SetupEvent.Confirmed)
        assertEquals(PairStep.ROKU_HINT, limited.step)
        assertFalse(limited.paired)
        assertTrue(limited.reduce(SetupEvent.Finish).paired)
    }

    @Test
    fun `a Fire TV with ADB off says so, and the prompt coming up clears it`() {
        val off = pairing(fire, SetupEvent.Link(Connection.Connecting), SetupEvent.Link(Connection.Offline), SetupEvent.Hint(SetupHint.FIRE_TV_ADB))
        assertEquals(PairStep.ADB_HINT, off.step)
        assertEquals(PairStep.APPROVE, off.reduce(SetupEvent.Link(Connection.AwaitingApproval)).step)
        val retried = off.reduce(SetupEvent.Retry)
        assertFalse(retried.asked)
        assertNull(retried.hint)
        assertFalse(retried.heard)
        assertEquals(PairStep.APPROVE, retried.step)
    }

    @Test
    fun `back from pairing is the list again`() {
        val setup = Setup().after(SetupEvent.Found(listOf(lg, samsung)), SetupEvent.ScanEnded, SetupEvent.Pick(lg), SetupEvent.Asked)
        val back = setup.reduce(SetupEvent.Back)
        assertNull(back.picked)
        assertFalse(back.asked)
        assertEquals(SetupScreen.Choose(listOf(lg, samsung), scanning = false), back.screen)
        assertEquals(SetupScreen.NothingFound, Setup().after(SetupEvent.ScanEnded, SetupEvent.Pick(lg), SetupEvent.Back).screen)
    }

    @Test
    fun `Wi-Fi going mid-pairing waits, then asks the TV again`() {
        val gone = pairing(lg, SetupEvent.Link(Connection.AwaitingApproval), SetupEvent.Wifi(false))
        assertEquals(SetupScreen.NoWifi, gone.screen)
        val back = gone.reduce(SetupEvent.Wifi(true))
        assertEquals(lg, back.picked)
        assertFalse(back.asked)
        assertFalse(back.scanning)
    }

    @Test
    fun `hints only for the kind they're about`() {
        assertEquals(SetupHint.ROKU_MOBILE_CONTROL, Notice.ROKU_LIMITED.asHint(TvKind.ROKU))
        assertEquals(SetupHint.FIRE_TV_ADB, Notice.ADB_OFF.asHint(TvKind.FIRE_TV))
        assertNull(Notice.ROKU_LIMITED.asHint(TvKind.WEBOS))
        assertNull(Notice.NOT_CONNECTED.asHint(TvKind.ROKU))
    }

    @Test
    fun `each kind pairs its own way`() {
        assertEquals(PairingStyle.APPROVE, TvKind.WEBOS.pairingStyle)
        assertEquals(PairingStyle.APPROVE, TvKind.SAMSUNG.pairingStyle)
        assertEquals(PairingStyle.APPROVE, TvKind.FIRE_TV.pairingStyle)
        assertEquals(PairingStyle.CODE, TvKind.ANDROID_TV.pairingStyle)
        assertEquals(PairingStyle.NONE, TvKind.ROKU.pairingStyle)
    }

    @Test
    fun `labels are a name, and the model or else the address`() {
        assertEquals(TvLabel("LG webOS", "UM7450PLA"), lg.label)
        assertEquals(TvLabel("Tenna", "192.168.1.70"), FoundTv("192.168.1.70", "[LG] Tenna").label)
        assertEquals(TvLabel("LG webOS", "192.168.1.70"), FoundTv("192.168.1.70", "LG webOS TV").label)
        assertEquals(TvLabel("Living Room", "QE55Q80T"), samsung.label)
        assertEquals(TvLabel("Philips Google TV", "192.168.1.50"), android.label)
        assertEquals(TvLabel("Roku Ultra", "192.168.1.40"), roku.copy(name = "Roku Ultra").label)
        assertEquals(SavedTv("LG webOS", "192.168.1.70", TvKind.WEBOS, ""), lg.toSaved())
    }

    @Test
    fun `a first run's TV replaces the defaults, which aren't kept to switch back to`() {
        val fresh = Prefs(tvChosen = false)
        val trying = fresh.trying(samsung.toSaved(), adding = false)
        assertEquals("192.168.1.30", trying.host)
        assertEquals(TvKind.SAMSUNG, trying.kind)
        assertEquals("Living Room", trying.tvName)
        assertTrue(trying.otherTvs.isEmpty())
        assertFalse(trying.tvChosen)
        assertTrue(trying.settled().tvChosen)
        assertEquals(fresh, trying.untrying(fresh.tvChoice))
    }

    @Test
    fun `an added TV keeps the one in use to switch back to, and calling it off puts things back`() {
        val known = SavedTv("Bedroom Roku", "192.168.1.40", TvKind.ROKU, "aa:bb:cc:dd:ee:ff")
        val before = Prefs(otherTvs = listOf(known), demoTv = true)
        val trying = before.trying(samsung.toSaved(), adding = true)
        assertEquals("192.168.1.30", trying.host)
        assertFalse(trying.demoTv)
        assertEquals(listOf(known, SavedTv(before.tvName, before.host, before.kind, before.mac)).sortedBy { it.name.lowercase() }, trying.otherTvs)
        assertEquals(before, trying.untrying(before.tvChoice))
        val again = before.trying(roku.toSaved(), adding = true)
        assertEquals("Bedroom Roku", again.tvName)
        assertEquals("aa:bb:cc:dd:ee:ff", again.mac)
    }

    @Test
    fun `a TV tried and called off leaves no pad behind, one known already keeps its own`() {
        val known = SavedTv("Bedroom Roku", "192.168.1.40", TvKind.ROKU, "")
        val before = Prefs(host = "192.168.1.70", otherTvs = listOf(known))
            .withPad("192.168.1.70", touchpad = true, app = null)
            .withPad(known.host, touchpad = false, app = "youtube")
        val tried = before.trying(samsung.toSaved(), adding = true).let { it.withPad(it.tvKey, touchpad = true, app = "netflix") }
        assertEquals(before.touchpadModes, tried.untrying(before.tvChoice).touchpadModes)
        val again = before.trying(roku.toSaved(), adding = true).let { it.withPad(it.tvKey, touchpad = true, app = "netflix") }
        assertEquals(true, again.untrying(before.tvChoice).touchpadModes[known.host])
    }

    @Test
    fun `a typed address is whichever kind answers there, the first kind first`() = runTest {
        assertEquals(TvKind.ROKU, kindAt("10.0.0.2") { _, kind -> kind == TvKind.ROKU })
        assertEquals(TvKind.ANDROID_TV, kindAt("10.0.0.2") { _, kind -> kind == TvKind.FIRE_TV || kind == TvKind.ANDROID_TV })
        assertNull(kindAt("10.0.0.2") { _, _ -> false })
    }

    @Test
    fun `setup survives being saved and restored`() {
        val setup = pairing(android, SetupEvent.Link(Connection.AwaitingCode()), SetupEvent.Hint(SetupHint.FIRE_TV_ADB))
            .copy(found = listOf(lg, samsung, android))
        val scope = SaverScope { true }
        val saved = with(SetupSaver) { scope.save(setup) }!!
        assertEquals(setup.copy(link = null), SetupSaver.restore(saved))
        val choice = Prefs(otherTvs = listOf(SavedTv("Bedroom", "192.168.1.40", TvKind.ROKU, ""))).tvChoice
        assertEquals(choice, TvChoiceSaver.restore(with(TvChoiceSaver) { scope.save(choice) }!!))
    }

    @Test
    fun `discovery tells each kind's TVs as they answer`() = runTest {
        val discovery = TvDiscovery(
            listOf(
                {
                    delay(300)
                    listOf(roku)
                },
                {
                    delay(100)
                    listOf(lg, samsung)
                },
                { error("multicast blocked") },
                {
                    delay(200)
                    listOf(lg)
                },
            ),
        )
        assertEquals(listOf(listOf(lg, samsung), listOf(lg, samsung, roku)), discovery.stream().toList())
    }
}
