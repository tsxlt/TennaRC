import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `no git at all is a dev build with code 1`() {
        assertEquals(AppVersion("0.1.0-dev", 1), appVersionOf("0.1.0", describe = null, sha = null, committedAt = null))
    }

    @Test
    fun `a commit but no tag names the commit`() {
        assertEquals(AppVersion("0.1.0-dev+abc1234", 12), appVersionOf("0.1.0", describe = null, sha = "abc1234", committedAt = 12 * 60L))
    }

    @Test
    fun `a tagged commit is the tag without its v`() {
        assertEquals(AppVersion("0.2.0", 40), appVersionOf("0.1.0", describe = "v0.2.0\n", sha = "abc1234", committedAt = 40 * 60L))
        assertEquals(AppVersion("1.0.0", 41), appVersionOf("0.1.0", describe = "1.0.0", sha = "abc1234", committedAt = 41 * 60L + 59))
    }

    @Test
    fun `past a tag says how far`() {
        assertEquals(
            AppVersion("0.2.0-3-gabc1234", 43),
            appVersionOf("0.1.0", describe = "v0.2.0-3-gabc1234", sha = "abc1234", committedAt = 43 * 60L),
        )
    }

    @Test
    fun `blank answers count as none`() {
        assertEquals(AppVersion("0.1.0-dev", 1), appVersionOf("0.1.0", describe = " ", sha = "", committedAt = 0))
    }

    @Test
    fun `an amend a minute later is a newer code`() {
        val before = appVersionOf("0.1.0", null, "aaaaaaa", 1_791_747_529).code
        val after = appVersionOf("0.1.0", null, "bbbbbbb", 1_791_747_589).code
        assertEquals(1, after - before)
    }

    @Test
    fun `it stays above the commit counts it replaced`() {
        assertTrue(appVersionOf("0.1.0", null, "aaaaaaa", 1_791_747_529).code > 303)
    }
}
