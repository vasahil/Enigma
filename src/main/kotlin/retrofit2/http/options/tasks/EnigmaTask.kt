package retrofit2.http.options.tasks

import retrofit2.http.options.EnigmaPluginExtension
import retrofit2.http.options.parser.JavaCode
import retrofit2.http.options.parser.JavaParser
import retrofit2.http.options.utils.AESUtils
import retrofit2.http.options.utils.MaskUtils
import retrofit2.http.options.utils.TextUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Gradle Task to parse JAVA/Kotlin source code and encrypt/mask string values.
 */
@DisableCachingByDefault(because = "Encrypts source files in place")
open class EnigmaTask @Inject constructor() : AbstractTask() {

    @Internal
    var hash: String? = null
    @Internal
    var ignoredClasses: Array<String>? = null
    @Internal
    var classes: Array<String>? = null
    @Internal
    var customFunction: String? = null
    @Internal
    var customEncryptionTask: DefaultTask? = null
    @Internal
    var injectFakeKeys = true
    @Internal
    var maskKeywords: Array<String> = arrayOf("lowercase", "uppercase", "trim")
    @Internal
    var maskWordsCount: Int = 5
    @Internal
    var customWords: Array<String>? = null
    @Internal
    var salt: String = MaskUtils.DEFAULT_SALT

    @TaskAction
    @Throws(Exception::class)
    fun encrypt() {
        if (!enabled) return
        if (!checkSCM()) return

        if (TextUtils.isEmpty(this.hash) && this.customEncryptionTask == null) {
            println("⚠️ Missing Hash value to encrypt files (or Custom Encryption Task)")
            return
        } else if (!TextUtils.isEmpty(this.hash) && this.hash!!.length < AESUtils.MIN_KEY_SIZE) {
            println("⚠️ The secrete 'retrofit2.http.options.hash' must at least contains " + AESUtils.MIN_KEY_SIZE + " characters!")
            return
        }

        if (!backupDirExists()) {
            println("⚠️ Impossible to execute 'encrypt' task if backup directory not exists!")
            return
        }

        val isMasking = (mode == EnigmaPluginExtension.MODE_MASKING)
        val globalMaskMapping = HashMap<String, Pair<String, String>>()
        val allUsedKeywords = HashSet<String>()

        for (srcFile in this.getAllSourceFiles()) {
            if (!isSelected(srcFile) || isIgnored(srcFile)) {
                println("🚫 " + srcFile.name + " ignored")
            } else if (srcFile.name.endsWith(".kt")) {
                if (isMasking) {
                    val used = maskKotlinFile(srcFile, globalMaskMapping)
                    allUsedKeywords.addAll(used)
                } else {
                    encryptKotlinFile(srcFile)
                }
            } else {
                encryptJavaFile(srcFile)
            }
        }

        if (isMasking && globalMaskMapping.isNotEmpty()) {
            writeMaskedStringKt(globalMaskMapping, allUsedKeywords)
        }
    }

    private fun isSelected(srcFile: File): Boolean {
        if (this.classes != null) {
            for (selected in this.classes!!) {
                if (matchesClass(srcFile, selected)) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private fun isIgnored(srcFile: File): Boolean {
        if (this.ignoredClasses != null) {
            for (ignored in this.ignoredClasses!!) {
                if (matchesClass(srcFile, ignored)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Match a source file against a class entry from the config.
     * Entry can be a package path (com.foo.Bar) with optional .java/.kt suffix;
     * matches both Java and Kotlin files for backward compatibility.
     */
    private fun matchesClass(srcFile: File, entry: String): Boolean {
        var name = entry.trim()
        if (name.endsWith(".java")) name = name.substring(0, name.length - 5)
        else if (name.endsWith(".kt")) name = name.substring(0, name.length - 3)
        val path = name.replace(".", File.separator)
        val absolutePath = srcFile.absolutePath
        return absolutePath.endsWith(path + ".java") ||
            absolutePath.endsWith(path + ".kt") ||
            absolutePath.endsWith(path)
    }

    private fun encryptJavaFile(srcFile: File) {
        if (isEnigmaFile(srcFile)) return
        if (isEnigmatized(srcFile)) {
            println("⚠️ Cannot process a file already encrypted: $srcFile")
            return
        }

        val contents = FileUtils.readFileToString(srcFile, "UTF-8")

        val p = JavaParser()
        val code = p.parse(contents)

        code.addImport(InjectCodeTask.PACKAGE_NAME + "." + InjectCodeTask.CLASS_NAME)
        code.encryptStrings(hash!!, InjectCodeTask.FUNCTION_NAME)

        if (injectFakeKeys) code.injectFakeKeys()

        val contentSecured = code.toCode()
        FileUtils.writeStringToFile(srcFile, contentSecured, "UTF-8")

        println("\uD83D\uDD10 " + srcFile.name + " encrypted")
    }

    private fun encryptKotlinFile(srcFile: File) {
        if (isEnigmaFile(srcFile)) return
        if (isEnigmatized(srcFile)) {
            println("⚠️ Cannot process a file already encrypted: $srcFile")
            return
        }

        var contents = FileUtils.readFileToString(srcFile, "UTF-8")

        // Raw triple-quoted strings can't be mapped safely: skip the file
        if (contents.contains("\"\"\"")) {
            println("⚠️ " + srcFile.name + " skipped (raw triple-quoted strings not supported)")
            return
        }

        // Kotlin imports have no trailing semicolon: inject via plain text
        if (!contents.contains(InjectCodeTask.KOTLIN_IMPORT_NAME)) {
            contents = if (contents.contains("package ")) {
                contents.replaceFirst(
                    "(package [^\\n]+\\n)".toRegex(),
                    "\$1" + InjectCodeTask.KOTLIN_IMPORT_NAME + "\n"
                )
            } else {
                InjectCodeTask.KOTLIN_IMPORT_NAME + "\n" + contents
            }
        }

        val p = JavaParser()
        val code = p.parse(contents)

        code.encryptStringsKotlin(hash!!, InjectCodeTask.FUNCTION_NAME)

        if (injectFakeKeys) code.injectFakeKeysKotlin()

        val contentSecured = code.toCode()
        FileUtils.writeStringToFile(srcFile, contentSecured, "UTF-8")

        println("\uD83D\uDD10 " + srcFile.name + " encrypted")
    }

    private fun maskKotlinFile(srcFile: File, globalMapping: MutableMap<String, Pair<String, String>>): Set<String> {
        if (isEnigmaFile(srcFile)) return emptySet()
        if (isEnigmatized(srcFile)) {
            println("⚠️ Cannot process a file already encrypted: $srcFile")
            return emptySet()
        }

        var contents = FileUtils.readFileToString(srcFile, "UTF-8")

        // Raw triple-quoted strings can't be mapped safely: skip the file
        if (contents.contains("\"\"\"")) {
            println("⚠️ " + srcFile.name + " skipped (raw triple-quoted strings not supported)")
            return emptySet()
        }

        val p = JavaParser()
        val code = p.parse(contents)

        val pkg = maskPackage ?: MaskUtils.DEFAULT_PACKAGE
        val profile = hash ?: pkg
        val saltHex = salt ?: MaskUtils.DEFAULT_SALT

        val usedKeywords = code.maskStringsKotlin(
            profile = profile,
            saltHex = saltHex,
            keywords = maskKeywords.toList(),
            globalMapping = globalMapping,
            wordCount = maskWordsCount,
            customWords = customWords?.toList()
        )

        if (injectFakeKeys) code.injectFakeKeysKotlin()

        var contentSecured = code.toCode()

        for (kw in usedKeywords) {
            val importStmt = "import $pkg.$kw"
            if (!contentSecured.contains(importStmt)) {
                contentSecured = if (contentSecured.contains("package ")) {
                    contentSecured.replaceFirst(
                        "(package [^\\n]+\\n)".toRegex(),
                        "\$1$importStmt\n"
                    )
                } else {
                    "$importStmt\n$contentSecured"
                }
            }
        }

        FileUtils.writeStringToFile(srcFile, contentSecured, "UTF-8")
        println("🔐 " + srcFile.name + " masked")
        return usedKeywords
    }

    private fun writeMaskedStringKt(
        mapping: Map<String, Pair<String, String>>,
        keywords: Set<String>
    ) {
        val kotlinSrc = if (!TextUtils.isEmpty(pathSrcKotlin)) pathSrcKotlin else pathSrc
        val pkg = maskPackage ?: MaskUtils.DEFAULT_PACKAGE
        val kotlinPackage = File(kotlinSrc + File.separator + pkg.replace(".", File.separator))
        if (!kotlinPackage.exists()) kotlinPackage.mkdirs()

        val kotlinCodeFile = File(kotlinPackage.absolutePath + File.separator + "String.kt")
        val profile = hash ?: pkg
        val saltHex = salt ?: MaskUtils.DEFAULT_SALT
        val effectiveKeywords = if (keywords.isNotEmpty()) keywords else maskKeywords.toSet()

        val source = MaskUtils.generateStringKtSource(pkg, profile, saltHex, mapping, effectiveKeywords)
        FileUtils.writeStringToFile(kotlinCodeFile, source, "UTF-8")
        println("✏️ Updated MaskWeb String.kt with ${mapping.size} masked strings")
    }
}
