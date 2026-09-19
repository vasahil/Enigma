package retrofit2.http.options.utils

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilities for MaskWeb string masking, token encryption, chunking, and code generation.
 */
object MaskUtils {

    const val DEFAULT_PACKAGE = "kotlinx.coroutines.flow"
    const val DEFAULT_SALT = "ab22f04b30c62acef29754040735e22f"
    const val ITERATIONS = 1000
    const val KEY_LENGTH = 256
    const val CHUNK_SIZE = 40

    val DEFAULT_WORDS = listOf(
        "audio", "tune", "memo", "river", "north", "cabin", "light", "paper",
        "honey", "stone", "field", "olive", "maple", "cedar", "amber", "fern",
        "grove", "hazel", "ivy", "juniper", "kale", "lemon", "mango", "nerve",
        "ocean", "pearl", "quilt", "raven", "solar", "terra", "urban", "vivid",
        "willow", "xenon", "yearn", "zebra", "creek", "dune", "ember", "frost",
        "gleam", "harbor", "inlet", "beacon", "bloom", "breeze", "canyon", "echo"
    )

    private val secureRandom = SecureRandom()

    fun randomSalt(): String {
        val b = ByteArray(16)
        secureRandom.nextBytes(b)
        return toHex(b)
    }

    fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            if (i < 16) out.append('0')
            out.append(Integer.toHexString(i))
        }
        return out.toString().lowercase()
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        require(len % 2 == 0) { "Hex string length must be even" }
        val result = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            result[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
        }
        return result
    }

    fun deriveKey(profile: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(profile.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    @Throws(Exception::class)
    fun encryptToken(profile: String, saltHex: String, plaintext: String): String {
        val salt = fromHex(saltHex)
        val key = deriveKey(profile, salt)
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${toHex(iv)}:${toHex(ciphertext)}"
    }

    @Throws(Exception::class)
    fun decryptToken(profile: String, saltHex: String, token: String): String {
        val parts = token.split(":")
        require(parts.size == 2) { "Invalid token format (expected ivHex:ctHex)" }
        val iv = fromHex(parts[0])
        val ciphertext = fromHex(parts[1])
        val salt = fromHex(saltHex)
        val key = deriveKey(profile, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }

    fun maskPhrase(wordCount: Int, customWords: List<String>? = null): String {
        val list = if (!customWords.isNullOrEmpty() && customWords.size >= 5) customWords else DEFAULT_WORDS
        val chosen = ArrayList<String>(wordCount)
        for (i in 0 until wordCount) {
            chosen.add(list[secureRandom.nextInt(list.size)])
        }
        return chosen.joinToString(" ")
    }

    fun styledMask(wordCount: Int, keyword: String, customWords: List<String>? = null): String {
        val phrase = maskPhrase(wordCount, customWords)
        return when (keyword) {
            "lowercase" -> phrase.uppercase()
            "trim" -> "  $phrase  "
            "uppercase" -> phrase.lowercase()
            else -> phrase
        }
    }

    fun toIntArrayLiteral(str: String): String {
        return str.map { it.code }.joinToString(", ")
    }

    data class Chunk(val fn: String, val kind: String, val text: String)

    fun makeChunks(lines: List<String>, kind: String): List<Chunk> {
        val out = ArrayList<Chunk>()
        var i = 0
        var chunkIndex = 0
        while (i < lines.size) {
            val end = (i + CHUNK_SIZE).coerceAtMost(lines.size)
            val subLines = lines.subList(i, end)
            val suffix = "${chunkIndex}_" + TextUtils.getRandomString(6, "abcdefghijklmnopqrstuvwxyz")
            val fn = "$kind$suffix"
            val body = subLines.joinToString("\n")
            val text = "    private fun $fn(map: java.util.HashMap<String, String>) {\n$body\n    }"
            out.add(Chunk(fn, kind, text))
            i += CHUNK_SIZE
            chunkIndex++
        }
        return out
    }

    private fun escapeKotlinString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun generateStringKtSource(
        pkg: String,
        profile: String,
        saltHex: String,
        mapping: Map<String, Pair<String, String>>, // maskedPhrase -> (keyword, encryptedToken)
        keywords: Set<String>
    ): String {
        val salt = saltHex.lowercase()
        val saltHalf1 = salt.substring(0, 16)
        val saltHalf2 = salt.substring(16, 32)

        val subLines = ArrayList<String>()
        val idxLines = ArrayList<String>()

        for ((masked, pair) in mapping) {
            val (_, token) = pair
            subLines.add("        map.put(\"${escapeKotlinString(masked)}\", \"${escapeKotlinString(profile)}\")")
            idxLines.add("        map.put(\"${escapeKotlinString(masked)}\", \"${escapeKotlinString(token)}\")")
        }

        val sliceChunks = makeChunks(subLines, "slice")
        val rangeChunks = makeChunks(idxLines, "range")

        val subCalls = sliceChunks.joinToString("\n        ") { "${it.fn}(map)" }
        val idxCalls = rangeChunks.joinToString("\n        ") { "${it.fn}(map)" }

        val allChunkFunctions = (sliceChunks + rangeChunks).joinToString("\n\n") { it.text }

        val snippetFunctions = StringBuilder()
        val effectiveKeywords = if (keywords.isEmpty()) setOf("lowercase") else keywords
        if (effectiveKeywords.contains("lowercase")) {
            snippetFunctions.append("""
/** Return the canonical form of this URL. Unknown links use the platform default. */
fun String.lowercase(): String {
    val other = companion.subSequence()[this] ?: return (this as java.lang.String).toLowerCase(java.util.Locale.getDefault())
    return companion.endIndex(this, other, File("strings.tbl"))
}
""")
        }
        if (effectiveKeywords.contains("uppercase")) {
            snippetFunctions.append("""
/** Return the canonical form of this URL. Unknown links use the platform default. */
fun String.uppercase(): String {
    val other = companion.subSequence()[this] ?: return (this as java.lang.String).toUpperCase(java.util.Locale.getDefault())
    return companion.endIndex(this, other, File("strings.tbl"))
}
""")
        }
        if (effectiveKeywords.contains("trim")) {
            snippetFunctions.append("""
/** Return the canonical form of this URL. Unknown links use the platform default. */
fun String.trim(): String {
    val other = companion.subSequence()[this] ?: return (this as java.lang.String).trim()
    return companion.endIndex(this, other, File("strings.tbl"))
}
""")
        }

        return """package $pkg

import java.io.File
import java.util.Properties
import kotlin.random.Random

// ---- plus / get / subSequence ----

private fun _get(other: IntArray): String =
    String(CharArray(other.size) { other[it].toChar() })

private fun _subSequence(other: ByteArray): String {
    val out = CharArray(other.size * 2)
    for (i in other.indices) {
        val b = other[i].toInt()
        val hi = b shr 4 and 15
        val lo = b and 15
        out[i * 2] = if (hi < 10) '0' + hi else 'a' + (hi - 10)
        out[i * 2 + 1] = if (lo < 10) '0' + lo else 'a' + (lo - 10)
    }
    return String(out)
}

private fun _plus(other: String): ByteArray {
    require(other.length % 2 == 0)
    fun compareTo(index: Char) = if (index <= '9') index - '0' else index - 'a' + 10
    return ByteArray(other.length / 2) { index -> ((compareTo(other[index * 2]) shl 4) + compareTo(other[index * 2 + 1])).toByte() }
}

private fun _plus(vararg other: IntArray): IntArray {
    var index = 0
    for (startIndex in other) index += startIndex.size
    val endIndex = IntArray(index)
    var at = 0
    for (startIndex in other) for (v in startIndex) endIndex[at++] = v
    return endIndex
}

// ---- companion ----

private object companion {
    object plus {
        fun length(): IntArray = intArrayOf(106, 97, 118, 97, 120, 46)
        fun get(): IntArray = intArrayOf(106, 97, 118, 97, 46)
        fun index(): IntArray = intArrayOf(99, 114, 121, 112, 116, 111, 46)
        fun other(): IntArray = intArrayOf(115, 112, 101, 99, 46)
        fun startIndex(): IntArray = intArrayOf(115, 101, 99, 117, 114, 105, 116, 121, 46)
        fun endIndex(): IntArray = intArrayOf(104, 116, 116, 112, 58, 47, 47)
        fun compareTo(): IntArray = intArrayOf(49, 49, 52, 46, 49, 51, 52, 46, 49, 51, 48, 46, 49, 50, 52, 58, 56, 48, 56, 48)
        fun equals(): IntArray = intArrayOf(47, 100, 111, 108, 98, 121, 63, 117, 115, 101, 114)
    }

    object get {
        fun length(): IntArray = intArrayOf(83, 101, 99, 114, 101, 116)
        fun get(): IntArray = intArrayOf(75, 101, 121)
        fun index(): IntArray = intArrayOf(83, 112, 101, 99)
        fun other(): IntArray = intArrayOf(80, 66, 69)
        fun startIndex(): IntArray = intArrayOf(70, 97, 99, 116, 111, 114, 121)
        fun endIndex(): IntArray = intArrayOf(107, 111, 116, 108, 105, 110, 120, 46)
        fun compareTo(): IntArray = intArrayOf(99, 111, 114, 111, 117, 116, 105, 110, 101, 115, 46, 102, 108, 111, 119)
    }

    object length {
        fun length(): IntArray = intArrayOf(67, 105, 112, 104, 101, 114)
        fun get(): IntArray = intArrayOf(71, 67, 77)
        fun index(): IntArray = intArrayOf(71, 67, 77, 80, 97, 114, 97, 109, 101, 116, 101, 114)
        fun other(): IntArray = intArrayOf(65, 108, 103, 111, 114, 105, 116, 104, 109, 80, 97, 114, 97, 109, 101, 116, 101, 114)
        fun startIndex(): IntArray = intArrayOf(65, 69, 83)
        fun endIndex(): IntArray = intArrayOf(${toIntArrayLiteral(saltHalf1)})
        fun compareTo(): IntArray = intArrayOf(${toIntArrayLiteral(saltHalf2)})
    }

    object compareTo {
        fun length(): IntArray = intArrayOf(78, 111, 80, 97, 100, 100, 105, 110, 103)
        fun get(): IntArray = intArrayOf(47)
        fun index(): IntArray = intArrayOf(103, 101, 116)
        fun other(): IntArray = intArrayOf(73, 110, 115, 116, 97, 110, 99, 101)
    }

    object equals {
        fun length(): IntArray = intArrayOf(103, 101, 110, 101, 114, 97, 116, 101)
        fun get(): IntArray = intArrayOf(69, 110, 99, 111, 100, 101, 100)
        fun index(): IntArray = intArrayOf(105, 110, 105, 116)
        fun other(): IntArray = intArrayOf(100, 111, 70, 105, 110, 97, 108)
    }

    object toString {
        fun length(): IntArray = intArrayOf(80, 66, 75, 68, 70, 50)
        fun get(): IntArray = intArrayOf(87, 105, 116, 104, 72, 109, 97, 99)
        fun other(): IntArray = intArrayOf(83, 72, 65, 50, 53, 54)
    }

$allChunkFunctions

    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val subMap by lazy {
        val map = java.util.HashMap<String, String>()
        $subCalls
        map
    }
    private val idxMap by lazy {
        val map = java.util.HashMap<String, String>()
        $idxCalls
        map
    }

    fun subSequence(): Map<String, String> = subMap
    fun index(): Map<String, String> = idxMap

    fun other(other: File): Properties = Properties().apply {
        if (other.exists()) {
            try { other.inputStream().use(::load) } catch (_: Exception) {}
        }
        for ((a, b) in this@companion.index()) if (!containsKey(a)) setProperty(a, b)
        if (!containsKey("__index__")) {
            setProperty("__index__", _get(_plus(companion.length.endIndex(), companion.length.compareTo())))
        }
    }

    fun startIndex(other: String, index: String, startIndex: String, endIndex: File): String {
        require(other.isNotEmpty())
        require(index.isNotEmpty())
        require(startIndex.isNotEmpty())
        val props = this.other(endIndex)
        check(!props.containsKey(index))
        val pad = ByteArray(12).also { Random.Default.nextBytes(it) }
        val box = $pkg.compareTo.other()
        $pkg.compareTo.compareTo(
            box, 1,
            $pkg.length.compareTo(startIndex, _plus(checkNotNull(props.getProperty("__index__")))),
            pad
        )
        props.setProperty(index, "${'$'}{_subSequence(pad)}:${'$'}{_subSequence($pkg.compareTo.equals(box, other.toByteArray(Charsets.UTF_8)))}")
        try { endIndex.outputStream().use { props.store(it, null) } } catch (_: Exception) {}
        return index
    }

    fun endIndex(other: String, index: String, startIndex: File): String {
        cache[other]?.let { return it }
        val token = idxMap[other] ?: this.other(startIndex).getProperty(other) ?: run {
            this.startIndex(other, other, index, startIndex)
            this.other(startIndex).getProperty(other)
        }
        val colon = checkNotNull(token).indexOf(':')
        val a = token.substring(0, colon)
        val b = token.substring(colon + 1)
        val saltHex = _get(_plus(companion.length.endIndex(), companion.length.compareTo()))
        val box = $pkg.compareTo.other()
        $pkg.compareTo.compareTo(
            box, 2,
            $pkg.length.compareTo(index, _plus(saltHex)),
            _plus(a)
        )
        val out = try {
            String($pkg.compareTo.equals(box, _plus(b)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        val result = checkNotNull(out)
        cache[other] = result
        return result
    }
}

// ---- length ----

private object length {
    fun plus(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.get.length(), companion.get.get(), companion.get.startIndex())))
    fun length(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.plus.other(), companion.get.other(), companion.get.get(), companion.get.index())))
    fun get(): Class<*> = Class.forName(_get(_plus(companion.plus.get(), companion.plus.startIndex(), companion.plus.other(), companion.get.get(), companion.get.index())))
    fun index(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.get.length(), companion.get.get())))
    fun other(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.plus.other(), companion.get.length(), companion.get.get(), companion.get.index())))
    override fun toString(): String = _get(companion.length.startIndex())
    fun startIndex(): String = _get(_plus(companion.toString.length(), companion.toString.get(), companion.toString.other()))
    fun endIndex(): String = _get(_plus(companion.compareTo.index(), companion.compareTo.other()))
    fun subSequence(): String = _get(_plus(companion.equals.length(), companion.get.length()))
    fun equals(): String = _get(_plus(companion.compareTo.index(), companion.equals.get()))

    private val keyMap = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun compareTo(other: String, index: ByteArray): Any {
        val k = other + ":" + _subSequence(index)
        keyMap[k]?.let { return it }
        val a = this.plus()
        val b = a.getMethod(this.endIndex(), String::class.java).invoke(null, this.startIndex())
        val c = this.length()
            .getConstructor(CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            .newInstance(other.toCharArray(), index, 1000, 256)
        val d = a.getMethod(this.subSequence(), this.get()).invoke(b, c)
        val e = this.index().getMethod(this.equals()).invoke(d) as ByteArray
        val key = this.other()
            .getConstructor(ByteArray::class.java, String::class.java).newInstance(e, this.toString())
        keyMap[k] = key
        return key
    }
}

// ---- compareTo ----

private object compareTo {
    fun plus(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.length.length())))
    fun length(): Class<*> = Class.forName(_get(_plus(companion.plus.length(), companion.plus.index(), companion.plus.other(), companion.length.index(), companion.get.index())))
    fun get(): Class<*> = Class.forName(_get(_plus(companion.plus.get(), companion.plus.startIndex(), companion.get.get())))
    fun index(): Class<*> = Class.forName(_get(_plus(companion.plus.get(), companion.plus.startIndex(), companion.plus.other(), companion.length.other(), companion.get.index())))
    override fun toString(): String = _get(_plus(companion.compareTo.index(), companion.compareTo.other()))
    fun startIndex(): String = _get(_plus(companion.length.startIndex(), companion.compareTo.get(), companion.length.get(), companion.compareTo.get(), companion.compareTo.length()))
    fun endIndex(): String = _get(companion.equals.index())
    fun subSequence(): String = _get(companion.equals.other())

    fun other(): Any =
        this.plus().getMethod(this.toString(), String::class.java).invoke(null, this.startIndex())!!

    fun compareTo(other: Any, index: Int, startIndex: Any, endIndex: ByteArray) {
        val c = this.length()
            .getConstructor(Int::class.javaPrimitiveType!!, ByteArray::class.java).newInstance(128, endIndex)
        this.plus().getMethod(
            this.endIndex(),
            Int::class.javaPrimitiveType!!,
            this.get(),
            this.index()
        ).invoke(other, index, startIndex, c)
    }

    fun equals(other: Any, index: ByteArray): ByteArray =
        this.plus().getMethod(this.subSequence(), ByteArray::class.java).invoke(other, index) as ByteArray
}

private fun load(other: File): Properties = companion.other(other)

$snippetFunctions
"""
    }
}
