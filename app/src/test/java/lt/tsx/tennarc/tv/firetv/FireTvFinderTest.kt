package lt.tsx.tennarc.tv.firetv

import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.TvKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FireTvFinderTest {
    @Test
    fun `name and id come from the WhisperPlay TXT record`() {
        val txt = mapOf(
            "n" to "Living Room Fire TV".toByteArray(),
            "u" to "8d2c1f0e-1111-2222-3333-444455556666".toByteArray(),
            "s" to "0".toByteArray(),
            "tr" to "tcp".toByteArray(),
            "a" to null,
        )
        assertEquals(
            FoundTv("10.0.0.9", "Living Room Fire TV", "8d2c1f0e-1111-2222-3333-444455556666", TvKind.FIRE_TV),
            fireTv("amzn.dmgr:649079E36FF650C95604A3397843E704:QZT4YaSve2:325301", "10.0.0.9", txt),
        )
    }

    @Test
    fun `an unnamed one is just a Fire TV`() {
        assertEquals(FoundTv("10.0.0.9", "Fire TV", null, TvKind.FIRE_TV), fireTv("amzn.dmgr:649079E3:Q:1", "10.0.0.9", mapOf("n" to " ".toByteArray())))
        assertEquals("Den", fireTv("Den", "10.0.0.9", emptyMap()).name)
    }
}
