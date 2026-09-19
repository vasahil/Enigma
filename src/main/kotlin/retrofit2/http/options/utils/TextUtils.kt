package retrofit2.http.options.utils

/**
 * String helper functions
 */
object TextUtils {

    /**
     * Characters for random secrete key generation
     */
    const val KEY_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789qwertyuiopasdfghjklzxcvbnm#$*!?"

    /**
     * Characters for random attribute name
     */
    const val PARAM_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ_"

    /**
     * Check if a string ends with a specific character
     */
    fun endsWith(value: String?, character: Char): Boolean {
        if (value == null) return false
        val tempValue = value.trim()
        return tempValue[tempValue.length - 1] == character
    }

    /**
     * Check if the string is null or empty
     */
    fun isEmpty(value: String?): Boolean = value == null || value.isEmpty()

    /**
     * Generate a random string from character list
     */
    fun getRandomString(size: Int, chars: String): String {
        val random = java.util.Random()
        val sb = StringBuilder(size)
        for (i in 0 until size) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * Check if character is equals to 'space' or 'carrier return', or 'tabulation'
     */
    fun isEmptyChar(c: Char): Boolean = c == ' ' || c == '\r' || c == '\n' || c == '\t'

    /**
     * Check if Character is containing by the characters list
     */
    fun inCharactersList(list: CharArray, character: Char): Boolean {
        for (c in list) {
            if (c == character) return true
        }
        return false
    }

    /**
     * Check if the string is a space value
     */
    fun isSpace(s: String?): Boolean = s != null && s.trim().isEmpty()

    /**
     * Test if the character is a Return Character
     */
    fun isReturnChar(c: Char): Boolean = c == '\r' || c == '\n' || c == System.lineSeparator()[0]

    /**
     * Check if the String is equal to a Character
     */
    fun isEqualsToChar(s: String?, c: Char): Boolean = s != null && s[0] == c

    /**
     * Safe substring to avoid String Index Out Of Bounds Exception
     */
    fun safeSubstring(value: String, from: Int, to: Int): String? {
        val l = value.length
        if (l <= 0) return null
        return if (from > -1 && to > -1 && to <= l && from < to) {
            value.substring(from, to)
        } else {
            null
        }
    }
}
