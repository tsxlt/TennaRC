package lt.tsx.tennarc.tv

import lt.tsx.tennarc.icons.IconSource
import lt.tsx.tennarc.icons.StoreIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreIconsTest {
    @Test
    fun anAppIsItsOwnListing() {
        assertEquals(IconSource.Listing("com.disney.disneyplus"), StoreIcons.sourceOf(StoreIcons.url("com.disney.disneyplus")))
    }

    @Test
    fun aServiceShowsItsMarkNotItsName() {
        assertEquals(IconSource.Listing("com.netflix.mediaclient"), StoreIcons.sourceOf(StoreIcons.url("com.netflix.ninja")))
        assertEquals(IconSource.Site("youtube.com"), StoreIcons.sourceOf(StoreIcons.url("com.amazon.firetv.youtube")))
    }

    @Test
    fun onlyPackagesAreAskedAbout() {
        assertNull(StoreIcons.sourceOf("http://192.168.1.5/icon.png"))
        assertNull(StoreIcons.sourceOf("store://../../etc"))
        assertNull(StoreIcons.sourceOf("store://com.app&hl=x"))
    }

    @Test
    fun theListingsIconAtTheSizeAsked() {
        val page = """<html><meta property="og:image" content="https://play-lh.googleusercontent.com/abc_D-9=s0-br30"><meta"""
        assertEquals("https://play-lh.googleusercontent.com/abc_D-9=s192", StoreIcons.iconIn(page, 192))
        assertNull(StoreIcons.iconIn("""<meta property="og:image" content="https://evil.example/x.png">""", 192))
    }
}
