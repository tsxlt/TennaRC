package lt.tsx.tennarc.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextMirrorTest {
    @Test
    fun `typing appends`() = assertEquals(TextEdit(0, "o"), TextMirror.diff("hell", "hello"))

    @Test
    fun `backspace deletes one`() = assertEquals(TextEdit(1, ""), TextMirror.diff("hello", "hell"))

    @Test
    fun `autocorrect retypes from the first changed letter`() = assertEquals(TextEdit(2, "he"), TextMirror.diff("teh", "the"))

    @Test
    fun `an edit in the middle retypes everything after it`() = assertEquals(TextEdit(3, "Xbcd"), TextMirror.diff("abcd", "aXbcd"))

    @Test
    fun `no change sends nothing`() = assertNull(TextMirror.diff("same", "same"))

    @Test
    fun `applying the edit reproduces the new text`() {
        val pairs = listOf("" to "abc", "abc" to "", "teh" to "the", "abcd" to "aXbcd", "naïve" to "naive", "hi there" to "hi")
        for ((old, new) in pairs) {
            val edit = TextMirror.diff(old, new)!!
            assertEquals(new, old.dropLast(edit.delete) + edit.insert)
        }
    }
}
