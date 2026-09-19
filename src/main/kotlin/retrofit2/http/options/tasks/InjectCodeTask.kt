package retrofit2.http.options.tasks

import retrofit2.http.options.EnigmaPluginExtension
import retrofit2.http.options.utils.MaskUtils
import retrofit2.http.options.utils.TextUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Gradle Task to inject Enigma source code.
 */
@DisableCachingByDefault(because = "Generates decryptor source files in place")
open class InjectCodeTask @Inject constructor() : AbstractTask() {

    @Internal
    var hash: String? = null
    @Internal
    var customFunction: String? = null
    @Internal
    var maskKeywords: Array<String>? = null
    @Internal
    var salt: String? = null

    @TaskAction
    fun addCode() {
        if (!enabled) return
        if (!checkSCM()) return

        if (!TextUtils.isEmpty(customFunction)) return

        if (TextUtils.isEmpty(this.hash)) {
            println("⚠️ Missing Hash value to inject Enigma code")
            return
        }

        val hasJava = !getAllJavaFiles().isEmpty()
        val hasKotlin = !getAllKotlinFiles().isEmpty()

        if (hasJava) {
            val packageName = File(pathSrc + File.separator + PACKAGE_NAME.replace(".", File.separator))
            if (!packageName.exists()) packageName.mkdirs()

            val codeFile = File(packageName.absolutePath + File.separator + CLASS_NAME + ".java")
            val data = encodeHash(SOURCE_CODE)
            FileUtils.writeStringToFile(codeFile, data, "UTF-8")

            println("✏️ Add Enigma code")
        }

        if (hasKotlin) {
            val isMasking = (mode == EnigmaPluginExtension.MODE_MASKING)
            val kotlinSrc = if (!TextUtils.isEmpty(pathSrcKotlin)) pathSrcKotlin else pathSrc

            if (isMasking) {
                val pkg = maskPackage ?: MaskUtils.DEFAULT_PACKAGE
                val kotlinPackage = File(kotlinSrc + File.separator + pkg.replace(".", File.separator))
                if (!kotlinPackage.exists()) kotlinPackage.mkdirs()

                val kotlinCodeFile = File(kotlinPackage.absolutePath + File.separator + "String.kt")
                val profile = hash ?: pkg
                val saltHex = salt ?: MaskUtils.DEFAULT_SALT
                val kwSet = maskKeywords?.toSet() ?: setOf("lowercase", "uppercase", "trim")
                if (!kotlinCodeFile.exists()) {
                    val kotlinData = MaskUtils.generateStringKtSource(pkg, profile, saltHex, emptyMap(), kwSet)
                    FileUtils.writeStringToFile(kotlinCodeFile, kotlinData, "UTF-8")
                    println("✏️ Add MaskWeb String.kt code")
                }
            } else {
                val kotlinPackage = File(kotlinSrc + File.separator + PACKAGE_NAME.replace(".", File.separator))
                if (!kotlinPackage.exists()) kotlinPackage.mkdirs()

                val kotlinCodeFile = File(kotlinPackage.absolutePath + File.separator + CLASS_NAME + ".kt")
                val kotlinData = encodeKotlinHash(KOTLIN_SOURCE_CODE)
                FileUtils.writeStringToFile(kotlinCodeFile, kotlinData, "UTF-8")

                println("✏️ Add Enigma Kotlin code")
            }
        }
    }

    private fun encodeHash(source: String): String {
        val builder = StringBuilder()
        val key = this.hash ?: ""
        builder.append("{")
        for (i in key.indices) {
            val c = key[i]
            builder.append(c.code)
            if (i < key.length - 1) builder.append(", ")
        }
        builder.append("}")
        return source.replace("{0, 0}", builder.toString())
    }

    private fun encodeKotlinHash(source: String): String {
        val builder = StringBuilder()
        val key = this.hash ?: ""
        builder.append("intArrayOf(")
        for (i in key.indices) {
            val c = key[i]
            builder.append(c.code)
            if (i < key.length - 1) builder.append(", ")
        }
        builder.append(")")
        return source.replace("intArrayOf(0, 0)", builder.toString())
    }

    companion object {
        const val PACKAGE_NAME = "retrofit2.http.options"
        const val CLASS_NAME = "EnigmaUtils"
        const val FUNCTION_NAME = "$CLASS_NAME.enigmatization"
        const val IMPORT_NAME = "import $PACKAGE_NAME.$CLASS_NAME;"
        const val KOTLIN_IMPORT_NAME = "import $PACKAGE_NAME.$CLASS_NAME"

        val SOURCE_CODE = "package $PACKAGE_NAME;\n" +
            "\n" +
            "import javax.crypto.Cipher;\n" +
            "import javax.crypto.SecretKey;\n" +
            "import javax.crypto.spec.SecretKeySpec;\n" +
            "import javax.crypto.spec.IvParameterSpec;\n" +
            "\n" +
            "public class $CLASS_NAME {\n" +
            "   private final static int[] data = {0, 0};\n" +
            "   public static String enigmatization(byte[] enc) {\n" +
            "        try {\n" +
            "            byte[] keyValue  = keyToBytes(data);\n" +
            "            byte[] result = decrypt(keyValue, enc);\n" +
            "            return new String(result);\n" +
            "        } catch (Exception e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "        return null;\n" +
            "    }\n" +
            "    private static byte[] decrypt(byte[] keyValue, byte[] encrypted)\n" +
            "            throws Exception {\n" +
            "        SecretKey skeySpec = new SecretKeySpec(keyValue, \"AES\");\n" +
            "        Cipher cipher = Cipher.getInstance(\"AES/CBC/PKCS5Padding\");\n" +
            "        byte[] iv = new byte[cipher.getBlockSize()];\n" +
            "        IvParameterSpec ivParams = new IvParameterSpec(iv);\n" +
            "        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivParams);\n" +
            "        return cipher.doFinal(encrypted);\n" +
            "    }\n" +
            "    private static byte[] keyToBytes(int[] key) {\n" +
            "        int size = 16 * (key.length / 16);\n" +
            "        StringBuilder builder = new StringBuilder();\n" +
            "        for (int i = 0; i < size; i++) {\n" +
            "            builder.append((char) key[i]);\n" +
            "        }\n" +
            "        return builder.toString().getBytes();\n" +
            "    }\n" +
            "}\n"

        val KOTLIN_SOURCE_CODE = "package $PACKAGE_NAME\n" +
            "\n" +
            "import javax.crypto.Cipher\n" +
            "import javax.crypto.spec.SecretKeySpec\n" +
            "import javax.crypto.spec.IvParameterSpec\n" +
            "\n" +
            "object $CLASS_NAME {\n" +
            "    private val data = intArrayOf(0, 0)\n" +
            "    @JvmStatic\n" +
            "    fun enigmatization(enc: ByteArray): String? {\n" +
            "        return try {\n" +
            "            val keyValue = keyToBytes(data)\n" +
            "            val result = decrypt(keyValue, enc)\n" +
            "            String(result)\n" +
            "        } catch (e: Exception) {\n" +
            "            e.printStackTrace()\n" +
            "            null\n" +
            "        }\n" +
            "    }\n" +
            "    private fun decrypt(keyValue: ByteArray, encrypted: ByteArray): ByteArray {\n" +
            "        val skeySpec = SecretKeySpec(keyValue, \"AES\")\n" +
            "        val cipher = Cipher.getInstance(\"AES/CBC/PKCS5Padding\")\n" +
            "        val iv = ByteArray(cipher.blockSize)\n" +
            "        val ivParams = IvParameterSpec(iv)\n" +
            "        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivParams)\n" +
            "        return cipher.doFinal(encrypted)\n" +
            "    }\n" +
            "    private fun keyToBytes(key: IntArray): ByteArray {\n" +
            "        val size = 16 * (key.size / 16)\n" +
            "        val builder = StringBuilder()\n" +
            "        for (i in 0 until size) {\n" +
            "            builder.append(key[i].toChar())\n" +
            "        }\n" +
            "        return builder.toString().toByteArray()\n" +
            "    }\n" +
            "}\n"
    }
}
