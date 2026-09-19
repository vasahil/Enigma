package retrofit2.http.options

import retrofit2.http.options.parser.CodeBlock
import retrofit2.http.options.parser.CodeString
import retrofit2.http.options.parser.JavaCode
import retrofit2.http.options.parser.JavaParser
import retrofit2.http.options.tasks.InjectCodeTask
import retrofit2.http.options.utils.AESUtils
import retrofit2.http.options.utils.MaskUtils
import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.ArrayList
import java.util.regex.Pattern

class UnitTests {

    @Test
    fun encryption() {
        val key = Utils.randomHashKey()

        for (i in 0 until 100) {

            val valueSize = Utils.getRandomNumberInRange(10, 100)

            val value = TextUtils.getRandomString(valueSize, TextUtils.KEY_CHARACTERS)

            val encrypted = AESUtils.encrypt(key, value)

            val decrypted = AESUtils.decrypt(key, encrypted)

            Assert.assertEquals(value, decrypted)
        }
    }

    @Test
    fun testEnigmatization() {
        val javaFile = Utils.getFileResource("Utils.java")!!
        val originalCode = FileUtils.readFileToString(javaFile, "UTF-8")

        val parser = JavaParser()
        val c = parser.parse(originalCode)

        c.addImport(InjectCodeTask.IMPORT_NAME)

        c.injectFakeKeys("DMNGZONJKU", "moyvMeX1ESB3Q")

        c.encryptStrings("LXeyH4qdtk2YqNDnLqZzX5HmPEwEwZEN", InjectCodeTask.FUNCTION_NAME)

        val securedCode = c.toCode()

        val securedJavaFile = Utils.getFileResource("Utils-secured.java")!!
        val targetCode = FileUtils.readFileToString(securedJavaFile, "UTF-8")

        Assert.assertEquals(targetCode, securedCode)
    }

    @Test
    fun testKotlinEncryption() {
        val key = "LXeyH4qdtk2YqNDnLqZzX5HmPEwEwZEN"
        val kotlin = "package com.example\n" +
            "\n" +
            "class MainActivity {\n" +
            "    fun onCreate() {\n" +
            "        val message = \"Hello World\"\n" +
            "        val template = \"Hello \$name\"\n" +
            "        println(message)\n" +
            "    }\n" +
            "}\n"

        val parser = JavaParser()
        val c = parser.parse(kotlin)

        c.encryptStringsKotlin(key, InjectCodeTask.FUNCTION_NAME)
        c.injectFakeKeysKotlin("DMNGZONJKU", "moyvMeX1ESB3Q")

        val secured = c.toCode()

        // Plain string encrypted with Kotlin cipher style:
        Assert.assertTrue(secured.contains("EnigmaUtils.enigmatization(byteArrayOf"))
        Assert.assertFalse(secured.contains("\"Hello World\""))
        // $ string template stays literal:
        Assert.assertTrue(secured.contains("\"Hello \$name\""))
        // Fake honeypot key injected with Kotlin syntax:
        Assert.assertTrue(secured.contains("private val DMNGZONJKU = \"moyvMeX1ESB3Q\""))

        // Round-trip: embedded bytes decrypt back to the original value:
        val m = Pattern.compile("byteArrayOf\\(([-0-9, ]+)\\)").matcher(secured)
        Assert.assertTrue(m.find())
        val parts = m.group(1).split(",\\s*".toRegex())
        val enc = ByteArray(parts.size)
        for (i in parts.indices) enc[i] = parts[i].trim().toByte()
        Assert.assertEquals("Hello World", AESUtils.decrypt(key, AESUtils.toHex(enc)))
    }

    @Test
    fun testMaskUtilsTokenCrypto() {
        val profile = "kotlinx.coroutines.flow"
        val salt = MaskUtils.randomSalt()
        val testInputs = listOf(
            "Hello World!",
            "http://104.194.140.114:8080/api/v1/auth",
            "Special chars: !@#$%^&*()_+{}[]:;\"'\\|<>,.?/",
            "Non-ASCII: 日本語, العربية, Русский, हिंदी 🚀"
        )

        for (input in testInputs) {
            val token = MaskUtils.encryptToken(profile, salt, input)
            Assert.assertTrue(token.contains(":"))
            val decrypted = MaskUtils.decryptToken(profile, salt, token)
            Assert.assertEquals(input, decrypted)
        }
    }

    @Test
    fun testMaskPhraseAndStyling() {
        val lowerMask = MaskUtils.styledMask(5, "lowercase")
        Assert.assertEquals(5, lowerMask.split(" ").size)
        Assert.assertEquals(lowerMask.uppercase(), lowerMask)

        val upperMask = MaskUtils.styledMask(4, "uppercase")
        Assert.assertEquals(4, upperMask.split(" ").size)
        Assert.assertEquals(upperMask.lowercase(), upperMask)

        val trimMask = MaskUtils.styledMask(3, "trim")
        Assert.assertTrue(trimMask.startsWith("  "))
        Assert.assertTrue(trimMask.endsWith("  "))
    }

    @Test
    fun testMaskStringsKotlin() {
        val profile = "kotlinx.coroutines.flow"
        val salt = MaskUtils.DEFAULT_SALT
        val kotlin = "package com.example\n" +
            "\n" +
            "object Constants {\n" +
            "    const val TAG = \"FirebaseApi\"\n" +
            "}\n" +
            "@SerializedName(\"user_id\")\n" +
            "class MainActivity {\n" +
            "    fun onCreate() {\n" +
            "        val url = \"http://api.example.com/login\"\n" +
            "        val template = \"Hello \$name\"\n" +
            "        println(url)\n" +
            "    }\n" +
            "}\n"

        val parser = JavaParser()
        val c = parser.parse(kotlin)

        val globalMapping = HashMap<String, Pair<String, String>>()
        val usedKeywords = c.maskStringsKotlin(
            profile = profile,
            saltHex = salt,
            keywords = listOf("lowercase"),
            globalMapping = globalMapping,
            wordCount = 5
        )

        val secured = c.toCode()

        // Masked call present for normal string:
        Assert.assertTrue(secured.contains(".lowercase()"))
        Assert.assertFalse(secured.contains("\"http://api.example.com/login\""))
        // const val stays constant literal:
        Assert.assertTrue(secured.contains("const val TAG = \"FirebaseApi\""))
        // annotation argument stays literal:
        Assert.assertTrue(secured.contains("@SerializedName(\"user_id\")"))
        // $ string template stays literal:
        Assert.assertTrue(secured.contains("\"Hello \$name\""))
        Assert.assertEquals(1, globalMapping.size)

        // Decryption of the masked token matches the original plaintext:
        val entry = globalMapping.entries.first()
        val decrypted = MaskUtils.decryptToken(profile, salt, entry.value.second)
        Assert.assertEquals("http://api.example.com/login", decrypted)
    }

    @Test
    fun testChunkGeneration() {
        val lines = (1..95).map { "        map.put(\"$it\", \"$it\")" }
        val chunks = MaskUtils.makeChunks(lines, "slice")

        // 95 lines with chunk size 40 -> 3 chunks (40, 40, 15)
        Assert.assertEquals(3, chunks.size)
        for (chunk in chunks) {
            Assert.assertTrue(chunk.fn.startsWith("slice"))
            Assert.assertTrue(chunk.text.contains("private fun ${chunk.fn}(map: java.util.HashMap<String, String>) {"))
        }
    }

    @Test
    fun testStringKtSourceGeneration() {
        val profile = "kotlinx.coroutines.flow"
        val salt = MaskUtils.DEFAULT_SALT
        val mapping = mapOf(
            "AUDIO TUNE MEMO RIVER NORTH" to Pair("lowercase", MaskUtils.encryptToken(profile, salt, "Hello World")),
            "MAPLE CEDAR AMBER FERN GROVE" to Pair("trim", MaskUtils.encryptToken(profile, salt, "Secret Data"))
        )

        val src = MaskUtils.generateStringKtSource(
            pkg = "kotlinx.coroutines.flow",
            profile = profile,
            saltHex = salt,
            mapping = mapping,
            keywords = setOf("lowercase", "trim")
        )

        Assert.assertTrue(src.contains("package kotlinx.coroutines.flow"))
        Assert.assertTrue(src.contains("fun String.lowercase(): String"))
        Assert.assertTrue(src.contains("fun String.trim(): String"))
        Assert.assertTrue(src.contains("private object companion"))
        Assert.assertTrue(src.contains("fun subSequence(): Map<String, String>"))
        Assert.assertTrue(src.contains("fun index(): Map<String, String>"))
    }

    @Test
    fun testImportParser() {
        val code = "\nimport retrofit2.http.options.parser.JavaParser;"
        val javaParser = JavaParser()
        val javaCode = javaParser.parse(code)
        val blockImport = javaCode.getAllBlocks()[0]

        Assert.assertEquals(blockImport.type, CodeBlock.BlockType.Import)
        Assert.assertEquals(blockImport.name, "retrofit2.http.options.parser.JavaParser")
    }

    @Test
    fun testInsertArray() {

        val array1 = Utils.toArrayList(arrayOf(0, 1, 2, 3, 4, 5, 6))
        Utils.insertInArray(array1, 4, 99)
        val array2 = Utils.toArrayList(arrayOf(0, 1, 2, 3, 99, 4, 5, 6))

        Assert.assertEquals(array1, array2)
    }

    @Test
    fun testJavaParser() {
        try {
            val userHome = System.getProperty("user.home")

            val repoPath = userHome + File.separator + "android-aosp-launcher3" + File.separator
            val repo = File(repoPath)
            if (!repo.exists()) repo.mkdir()

            val src = File(repoPath + File.separator + "src" + File.separator)
            if (!src.exists()) {
                Git.cloneRepository()
                    .setURI("https://android.googlesource.com/platform/packages/apps/Launcher3")
                    .setDirectory(repo)
                    .call()
            }

            val javaFiles = Utils.listFileTree(src, ".java")

            for (javaFile in javaFiles) {
                testParseJavaFile(javaFile)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @param javaFile Java file to parse
     */
    @Throws(Exception::class)
    private fun testParseJavaFile(javaFile: File) {
        try {

            val originalCode = FileUtils.readFileToString(javaFile, "UTF-8")

            val parser = JavaParser()
            val c = parser.parse(originalCode)

            val generatedCode = c.toCode()
            Assert.assertNotNull(generatedCode)

            val stringValues = c.getStringValues()

            if (generatedCode.trim() == originalCode.trim()) {
                println("Parsing with success : " + javaFile.absolutePath)
            } else {
                println("Failed parsing: " + javaFile.absolutePath)
                Assert.assertEquals(originalCode.trim(), generatedCode.trim())
            }

            c.addImport("com.example.name")
            c.addFunction("public static boolean test(int value) { return value > 0; }")
            c.addAttribute("public String mValueTest = \"Hello World\";")

            c.injectFakeKeys()

            c.encryptStrings("LXeyH4qdtk2YqNDnLqZzX5HmPEwEwZEN", InjectCodeTask.FUNCTION_NAME)

            val securedCode = c.toCode()
            Assert.assertNotNull(securedCode)

        } catch (ex: Exception) {
            println(ex.javaClass.name + ": " + javaFile.absolutePath)
            throw ex
        }
    }

    @Test
    fun testRandomNumber() {
        var value: Int
        val min = 0
        val max = 10
        for (i in 0 until 100) {
            value = Utils.getRandomNumberInRange(min, max)
            Assert.assertTrue(value >= min)
            Assert.assertTrue(value <= max)
        }
    }

    @Test
    fun testArrayContains() {
        val arrayInt = arrayOf(9, 32, 2324, 10, 90, 0, 3)
        var result: Boolean

        result = Utils.arrayContains(arrayInt, 2324)
        Assert.assertTrue(result)

        result = Utils.arrayContains(arrayInt, 11)
        Assert.assertFalse(result)

        result = Utils.arrayContains(arrayInt, 3)
        Assert.assertTrue(result)

        result = Utils.arrayContains(arrayInt, 2)
        Assert.assertFalse(result)
    }

    @Test
    fun testTextIsEmptyChar() {
        Assert.assertTrue(TextUtils.isEmptyChar(' '))
        Assert.assertTrue(TextUtils.isEmptyChar('\r'))
        Assert.assertTrue(TextUtils.isEmptyChar('\n'))
        Assert.assertTrue(TextUtils.isEmptyChar('\t'))

        Assert.assertFalse(TextUtils.isEmptyChar('a'))
        Assert.assertFalse(TextUtils.isEmptyChar('0'))
        Assert.assertFalse(TextUtils.isEmptyChar('&'))
        Assert.assertFalse(TextUtils.isEmptyChar('.'))
        Assert.assertFalse(TextUtils.isEmptyChar('.'))
        Assert.assertFalse(TextUtils.isEmptyChar(';'))
        Assert.assertFalse(TextUtils.isEmptyChar('`'))
    }
}
