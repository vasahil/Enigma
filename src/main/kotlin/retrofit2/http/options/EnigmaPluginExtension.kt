package retrofit2.http.options

/**
 * Enigma Gradle Plugin Options
 */
open class EnigmaPluginExtension {
    /**
     * Enable or disable the Enigma Plugin
     */
    var enabled = true

    /**
     * Define the Key to encrypt Strings
     */
    var hash = ""

    /**
     * Default source folder (Android Java App)
     */
    var srcJava = "/app/src/main/java"

    /**
     * Default source folder for Kotlin files (usually same as srcJava)
     */
    var srcKotlin = "/app/src/main/java"

    /**
     * Define classes to ignore (no encryption)
     */
    var ignoredClasses: Array<String>? = null

    /**
     * Specified classes to encrypt. If null all classes are encrypted, excepted ignored classes.
     */
    var classes: Array<String>? = null

    /**
     * Enable / disable the fake keys injection (honeypot principal)
     */
    var injectFakeKeys = true

    /**
     * Enable / disable the DEBUG (verbose) mode
     */
    var debug = false

    /**
     * Custom Encryption function (not implemented)
     */
    var customFunction: String? = null

    /**
     * Custom Encryption task (not implemented)
     */
    var encryptionTaskName: String? = null

    /**
     * Obfuscation mode: "masking" (MaskWeb disguised keywords) or "classic" (byte array)
     */
    var mode: String = MODE_MASKING

    /**
     * Keywords for string masking method calls (e.g. ["lowercase", "uppercase", "trim"])
     */
    var maskKeywords: Array<String> = arrayOf("lowercase", "uppercase", "trim")

    /**
     * Package for the injected String.kt decryptor
     */
    var maskPackage: String = "kotlinx.coroutines.flow"

    /**
     * Number of random words per masked phrase
     */
    var maskWordsCount: Int = 5

    /**
     * Optional custom word dictionary for masked phrases
     */
    var customWords: Array<String>? = null

    /**
     * Salt for PBKDF2 key derivation (32 hex characters)
     */
    var salt: String = "ab22f04b30c62acef29754040735e22f"

    companion object {
        const val MODE_MASKING = "masking"
        const val MODE_CLASSIC = "classic"
    }
}
