package lt.tsx.tennarc.link

data class TextEdit(val delete: Int, val insert: String)

object TextMirror {
    fun diff(old: String, new: String): TextEdit? {
        if (old == new) return null
        val common = old.commonPrefixWith(new).length
        return TextEdit(delete = old.length - common, insert = new.substring(common))
    }
}
