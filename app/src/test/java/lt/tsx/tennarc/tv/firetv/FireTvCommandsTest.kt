package lt.tsx.tennarc.tv.firetv

import android.view.KeyEvent
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvPower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class FireTvCommandsTest {
    @Test
    fun `every button maps to a key code Android knows`() {
        fun known(name: String) = runCatching { KeyEvent::class.java.getField(name) }.isSuccess
        for (button in TvButton.entries) assertTrue("$button: ${keyCode(button)}", known(keyCode(button)))
        for (code in listOf(KEY_DELETE, KEY_ENTER, KEY_PLAY_PAUSE, KEY_VOLUME_UP, KEY_VOLUME_DOWN, KEY_MUTE, KEY_WAKE, KEY_SLEEP)) assertTrue(code, known(code))
    }

    @Test
    fun `remote buttons map to the d-pad and media keys`() {
        assertEquals("KEYCODE_DPAD_UP", keyCode(TvButton.UP))
        assertEquals("KEYCODE_DPAD_CENTER", keyCode(TvButton.ENTER))
        assertEquals("KEYCODE_BACK", keyCode(TvButton.BACK))
        assertEquals("KEYCODE_HOME", keyCode(TvButton.HOME))
        assertEquals("KEYCODE_HOME", keyCode(TvButton.EXIT))
        assertEquals("KEYCODE_MEDIA_PLAY", keyCode(TvButton.PLAY))
        assertEquals("KEYCODE_MEDIA_FAST_FORWARD", keyCode(TvButton.FAST_FORWARD))
        assertEquals("KEYCODE_7", keyCode(TvButton.DIGIT_7))
    }

    @Test
    fun `neighbouring keys share one input call`() {
        val line = inputCommand(listOf(key("KEYCODE_DPAD_UP"), key("KEYCODE_DPAD_UP"), key("KEYCODE_DPAD_CENTER")), TypedText())
        assertEquals("input keyevent KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_DPAD_CENTER", line)
    }

    @Test
    fun `quoting survives the shell untouched`() {
        assumeTrue(SH.canExecute())
        val nasty = listOf(
            "plain",
            "two words",
            "it's",
            "'''",
            "a;b&c|d",
            "\$HOME \${PATH} \$(id) `id`",
            "back\\slash \\' \\\\",
            "\"double\" 'single'",
            "*?[a]{b,c}~#!",
            "<in >out 2>&1",
            "tab\there",
            "-n",
        )
        for (text in nasty) assertEquals(text, sh("printf '%s' ${shellQuote(text)}"))
    }

    @Test
    fun `spaces go as percent-s`() {
        assertEquals(listOf("input text 'hello%sworld'"), textCommands("hello world"))
    }

    @Test
    fun `a literal percent-s splits between the two characters`() {
        assumeTrue(SH.canExecute())
        assertEquals(listOf("input text '100%'", "input text 'sure'"), textCommands("100%sure"))
        assertEquals("100%sure", typedOnTv("100%sure"))
    }

    @Test
    fun `what the TV types is what was asked for`() {
        assumeTrue(SH.canExecute())
        val samples = listOf(
            "hello world",
            "it's a \"test\"",
            "100% sure",
            "100%sure",
            "%s",
            "%%s",
            "% s",
            "50%",
            "a;b&c|d \$HOME `id` \\n",
            " leading and trailing ",
            "~!@#\$%^&*()_+{}|:<>?",
        )
        for (text in samples) assertEquals(text, typedOnTv(text))
    }

    @Test
    fun `only printable ASCII reaches input text`() {
        assertEquals("a", typeable('ą'))
        assertEquals("e", typeable('ė'))
        assertEquals("Z", typeable('Ž'))
        assertEquals("u", typeable('ū'))
        assertEquals("ss", typeable('ß'))
        assertEquals("l", typeable('ł'))
        assertEquals("'", typeable('’'))
        assertEquals("\"", typeable('“'))
        assertEquals("...", typeable('…'))
        assertEquals(" ", typeable(' '))
        assertEquals("fi", typeable('ﬁ'))
        assertEquals("", typeable('日'))
        assertEquals("", typeable('Ж'))
        assertEquals("", typeable('\n'))
        assertEquals("", "🎬".map(::typeable).joinToString(""))
    }

    @Test
    fun `typing goes through the transliteration`() {
        val line = inputCommand(listOf(text(0, "Ąžuolas ir “Šilas”")), TypedText())
        assertEquals("input text 'Azuolas%sir%s\"Silas\"'", line)
    }

    @Test
    fun `deleting counts what the TV got, not what the phone typed`() {
        val typed = TypedText()
        assertEquals(0 to "ssa", typed.apply(TextEdit(0, "ßa")))
        assertEquals(3 to "", typed.apply(TextEdit(2, "")))
        typed.apply(TextEdit(0, "x🎬"))
        assertEquals(1 to "y", typed.apply(TextEdit(3, "y")))
        assertEquals(4 to "", typed.apply(TextEdit(4, "")))
    }

    @Test
    fun `backspaces eat typing that hasn't gone out yet`() {
        val line = inputCommand(listOf(text(0, "helo"), text(1, ""), text(0, "lo")), TypedText())
        assertEquals("input text 'hello'", line)
    }

    @Test
    fun `backspaces past pending typing become delete keys`() {
        val typed = TypedText()
        typed.apply(TextEdit(0, "abc"))
        val line = inputCommand(listOf(text(0, "d"), text(3, "x")), typed)
        assertEquals("input keyevent KEYCODE_DEL KEYCODE_DEL; input text 'x'", line)
    }

    @Test
    fun `keys and typing keep their order`() {
        val line = inputCommand(
            listOf(key("KEYCODE_DPAD_DOWN"), text(0, "cats"), key(KEY_ENTER), text(0, "!")),
            TypedText(),
        )
        assertEquals("input keyevent KEYCODE_DPAD_DOWN; input text 'cats'; input keyevent KEYCODE_ENTER; input text '!'", line)
    }

    @Test
    fun `a batch that cancels out sends nothing`() {
        assertEquals("", inputCommand(listOf(text(0, "ab"), text(2, "")), TypedText()))
        assertEquals("", inputCommand(listOf(text(0, "日本")), TypedText()))
    }

    @Test
    fun `app list from launcher activities and known packages`() {
        val list = parseApps(APPS)
        assertEquals(
            listOf(FireTvApps.YOUTUBE, FireTvApps.NETFLIX, FireTvApps.PRIME_VIDEO, FireTvApps.SILK, "com.esaba.downloader", "org.videolan.vlc"),
            list.apps.map { it.id },
        )
        assertEquals(listOf("YouTube", "Netflix", "Prime Video", "Silk Browser", "Downloader", "VLC"), list.apps.map { it.title })
        assertEquals(listOf(true, false, false, false, false, false), list.apps.map { it.searchable })
        assertEquals(Launcher("com.netflix.ninja/.MainActivity", CATEGORY_LAUNCHER), list.launchers[FireTvApps.NETFLIX])
        assertEquals(Launcher("org.videolan.vlc/.gui.tv.MainTvActivity", CATEGORY_LEANBACK), list.launchers["org.videolan.vlc"])
        assertNull(list.launchers[FireTvApps.PRIME_VIDEO])
    }

    @Test
    fun `without cmd only known apps are listed`() {
        val output = """
            package:com.netflix.ninja
            package:com.android.systemui
            package:com.amazon.tv.launcher
            @launcher
            /system/bin/sh: cmd: not found
            @leanback
            /system/bin/sh: cmd: not found
        """.trimIndent()
        assertEquals(listOf(FireTvApps.NETFLIX), parseApps(output).apps.map { it.id })
    }

    @Test
    fun `apps the TV won't name get one from the package`() {
        assertEquals("Vlc", titleFromPackage("org.videolan.vlc"))
        assertEquals("Pluto", titleFromPackage("tv.pluto.android"))
        assertEquals("Downloader", titleFromPackage("com.esaba.downloader"))
        assertEquals("tv.android", titleFromPackage("tv.android"))
    }

    @Test
    fun `launching tries the launcher activity, then known ones, then monkey`() {
        assertEquals(
            listOf(
                "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n 'com.netflix.ninja/.MainActivity'",
                "monkey -p 'com.netflix.ninja' -c android.intent.category.LAUNCHER -c android.intent.category.LEANBACK_LAUNCHER --pct-syskeys 0 1",
            ),
            launchCommands(FireTvApps.NETFLIX, Launcher("com.netflix.ninja/.MainActivity", CATEGORY_LAUNCHER)),
        )
        val prime = launchCommands(FireTvApps.PRIME_VIDEO, null)
        assertEquals(3, prime.size)
        assertEquals("am start -n 'com.amazon.firebat/com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity'", prime[0])
        assertTrue(prime.last().startsWith("monkey -p 'com.amazon.firebat'"))
    }

    @Test
    fun `am start and monkey say when they failed`() {
        assertTrue(started("Starting: Intent { act=android.intent.action.VIEW dat=https://www.youtube.com/... pkg=com.amazon.firetv.youtube }"))
        assertTrue(started("Warning: Activity not started, its current task has been brought to the front"))
        assertTrue(started("  bash arg: -p\n  bash arg: com.netflix.ninja\nEvents injected: 1\n## Network stats: elapsed time=12ms"))
        assertFalse(started("Starting: Intent { ... }\nError: Activity not started, unable to resolve Intent { act=android.intent.action.VIEW }"))
        assertFalse(started("Error type 3\nError: Activity class {com.amazon.firebat/x.Y} does not exist."))
        assertFalse(started("** No activities found to run, monkey aborted."))
        assertFalse(started("java.lang.SecurityException: Permission Denial: starting Intent"))
    }

    @Test
    fun `links open in their apps`() {
        assertEquals(
            "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' -p 'com.amazon.firetv.youtube'",
            viewCommand(youTubeWatchUrl("dQw4w9WgXcQ"), FireTvApps.YOUTUBE, browsable = true),
        )
        assertEquals(
            "am start -n com.netflix.ninja/.MainActivity -a android.intent.action.VIEW -d 'netflix://title/80057281' -f 0x10000020 -e amzn_deeplink_data '80057281' -e source 30",
            netflixCommand("80057281"),
        )
        assertEquals(
            "am start -a android.intent.action.VIEW -d 'https://example.com/a?b=1&c='\\''d'\\''' -p 'com.amazon.cloud9'",
            viewCommand("https://example.com/a?b=1&c='d'", FireTvApps.SILK),
        )
    }

    @Test
    fun `a link reaches am exactly as given`() {
        assumeTrue(SH.canExecute())
        val url = "https://example.com/a b?q=\$HOME&x=`id`;y='z'#frag"
        val args = sh("am() { for a in \"\$@\"; do printf '[%s]' \"\$a\"; done; }; ${viewCommand(url, FireTvApps.SILK)}")
        assertEquals("[start][-a][android.intent.action.VIEW][-d][$url][-p][com.amazon.cloud9]", args)
    }

    @Test
    fun `YouTube searches go to its results page`() {
        assertEquals("https://www.youtube.com/results?search_query=lo-fi%20beats%20%26%20rain", youTubeSearchUrl("lo-fi beats & rain"))
    }

    @Test
    fun `every service app has a title`() {
        for ((service, apps) in FireTvApps.forService) for (app in apps) assertTrue("$service $app", app in FireTvApps.titles)
        assertTrue(StreamingService.NETFLIX in FireTvApps.forService)
    }

    @Test
    fun `wakefulness maps to power`() {
        assertEquals(TvPower.ON, parseWakefulness("  mWakefulness=Awake\n  mWakefulnessChanging=false"))
        assertEquals(TvPower.ON, parseWakefulness("  mWakefulness=Dreaming"))
        assertEquals(TvPower.STANDBY, parseWakefulness("  mWakefulness=Asleep"))
        assertEquals(TvPower.STANDBY, parseWakefulness("  mWakefulness=Dozing"))
        assertNull(parseWakefulness("/system/bin/sh: dumpsys: not found"))
    }

    @Test
    fun `focused app on Fire OS 7`() {
        val output = """
              mWakefulness=Awake
              mCurrentFocus=Window{2a1b3c4 u0 com.netflix.ninja/com.netflix.ninja.MainActivity}
              mFocusedApp=AppWindowToken{5d6e7f8 token=Token{9a0b1c2 ActivityRecord{3d4e5f6 u0 com.netflix.ninja/.MainActivity t42}}}
        """.trimIndent()
        assertEquals(FireTvApps.NETFLIX, parseFocusedApp(output))
    }

    @Test
    fun `focused app on Fire OS 8`() {
        val output = """
              mWakefulness=Awake
              mInputMethodTarget in display# 0 Window{8c1d2e3 u0 com.amazon.firetv.youtube/dev.cobalt.app.MainActivity}
              mInputMethodInputTarget in display# 0 Window{8c1d2e3 u0 com.amazon.firetv.youtube/dev.cobalt.app.MainActivity}
        """.trimIndent()
        assertEquals(FireTvApps.YOUTUBE, parseFocusedApp(output))
    }

    @Test
    fun `the keyboard or a toast in front doesn't hide the app`() {
        val output = """
              mCurrentFocus=Window{1f2e3d4 u0 InputMethod}
              mFocusedApp=AppWindowToken{7a8b9c0 token=Token{1d2e3f4 ActivityRecord{5a6b7c8 u0 com.amazon.tv.launcher/.ui.HomeActivity_vNext t1}}}
        """.trimIndent()
        assertEquals(FireTvApps.HOME, parseFocusedApp(output))
        assertNull(parseFocusedApp("  mCurrentFocus=null\n  mFocusedApp=null"))
    }

    @Test
    fun `link features follow the installed apps`() {
        val unknown = fireTvFeatures(emptyList())
        assertTrue(Feature.YOUTUBE_LINKS in unknown && Feature.NETFLIX_LINKS in unknown && Feature.WEB_LINKS in unknown)
        assertFalse(Feature.POINTER in unknown)
        assertFalse(Feature.VOLUME_LEVEL in unknown)
        val noBrowser = fireTvFeatures(listOf(TvApp(FireTvApps.NETFLIX, "Netflix")))
        assertEquals(setOf(Feature.TEXT_INPUT, Feature.CAPTIONS, Feature.APP_LIST, Feature.WAKE, Feature.NETFLIX_LINKS), noBrowser)
    }

    @Test
    fun `shell framing cuts one command's output out of the stream`() {
        assertNull(ShellFraming.output(3, "tenna:begin:3:\npartial"))
        assertEquals("line 1\nline 2", ShellFraming.output(3, "$ \ntenna:begin:3:\nline 1\nline 2\ntenna:end:3:\n"))
        assertEquals("no newline", ShellFraming.output(3, "tenna:begin:3:\nno newlinetenna:end:3:\n"))
        assertEquals("", ShellFraming.output(3, "tenna:begin:3:\ntenna:end:3:\n"))
        assertNull(ShellFraming.output(1, "tenna:begin:10:\nx\ntenna:end:10:\n"))
    }

    @Test
    fun `framed commands run in a real shell`() {
        assumeTrue(SH.canExecute())
        val script = ShellFraming.line(1, "echo one; echo two >&2; false") + ShellFraming.line(2, "printf 'a;b'") + ShellFraming.line(3, "cat")
        val output = sh(script)
        assertEquals("one\ntwo", ShellFraming.output(1, output))
        assertEquals("a;b", ShellFraming.output(2, output.substringAfter("tenna:end:1:")))
        assertEquals("", ShellFraming.output(3, output.substringAfter("tenna:end:2:")))
    }

    private fun key(code: String) = InputOp.Key(code)

    private fun text(delete: Int, insert: String) = InputOp.Text(TextEdit(delete, insert))

    private fun typedOnTv(text: String): String {
        val commands = textCommands(text.map(::typeable).joinToString(""))
        val args = sh("input() { printf '%s\\0' \"\$2\"; }; " + commands.joinToString("; "))
        return args.split('\u0000').dropLast(1).joinToString("") { inputText(it) }
    }

    private companion object {
        val SH = File("/bin/sh")

        val APPS = """
            package:com.amazon.tv.launcher
            package:com.netflix.ninja
            package:com.amazon.firetv.youtube
            package:com.amazon.firebat
            package:com.amazon.cloud9
            package:com.android.providers.settings
            package:org.videolan.vlc
            package:com.esaba.downloader
            @launcher
            com.amazon.tv.launcher/.ui.HomeActivity_vNext
            com.netflix.ninja/.MainActivity
            com.amazon.firetv.youtube/dev.cobalt.app.MainActivity
            com.amazon.cloud9/.browsing.BrowserActivity
            com.esaba.downloader/.ui.main.MainActivity
            @leanback
            org.videolan.vlc/.gui.tv.MainTvActivity
            com.netflix.ninja/.MainActivity
        """.trimIndent()

        fun sh(script: String): String {
            val process = ProcessBuilder(SH.path, "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            process.waitFor(10, TimeUnit.SECONDS)
            return output
        }

        fun inputText(arg: String): String {
            val buffer = StringBuilder(arg)
            var escape = false
            var i = 0
            while (i < buffer.length) {
                if (escape) {
                    escape = false
                    if (buffer[i] == 's') {
                        buffer.setCharAt(i, ' ')
                        buffer.deleteCharAt(--i)
                    }
                }
                if (buffer[i] == '%') escape = true
                i++
            }
            return buffer.toString()
        }
    }
}
