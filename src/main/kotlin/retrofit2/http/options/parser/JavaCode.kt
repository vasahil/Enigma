package retrofit2.http.options.parser

import retrofit2.http.options.tasks.InjectCodeTask
import retrofit2.http.options.utils.AESUtils
import retrofit2.http.options.utils.MaskUtils
import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils
import org.gradle.api.DefaultTask

/**
 * Map a Java file
 */
class JavaCode(
    private var rootCodeBlocks: ArrayList<CodeBlock>,
    private var codeStrings: ArrayList<CodeString>,
    private var sourceCode: String
) {

    /**
     * All String values
     */
    fun getStringValues(): ArrayList<CodeString> = this.codeStrings

    /**
     * Return all code blocks
     */
    fun getAllBlocks(): ArrayList<CodeBlock> = this.getAllBlocks(this.rootCodeBlocks)

    /**
     * Return all code blocks
     */
    private fun getAllBlocks(blocks: ArrayList<CodeBlock>?): ArrayList<CodeBlock> {
        val result = ArrayList<CodeBlock>()
        if (blocks == null) return result
        for (block in blocks) {
            result.add(block)
            if (Utils.arrayNotEmpty(block.subBlocks)) {
                val r = getAllBlocks(block.subBlocks)
                if (Utils.arrayNotEmpty(r)) result.addAll(r)
            }
        }
        return result
    }

    /**
     * Return of block of code from a start & end characters indexes.
     */
    private fun getBlockBetween(blocks: ArrayList<CodeBlock>, start: Int, end: Int): CodeBlock? {
        for (block in blocks) {
            if (Utils.arrayNotEmpty(block.subBlocks)) {
                val b = getBlockBetween(block.subBlocks, start, end)
                if (b != null) return b
            } else if (block.startWithOffset() <= start && end <= block.endWithOffset()) {
                return block
            }
        }
        return null
    }

    /**
     * Return Imports of JAVA file
     */
    fun getImports(): ArrayList<CodeBlock> = getBlocksByType(CodeBlock.BlockType.Import)

    /**
     * Return classes of JAVA file
     */
    fun getClasses(): ArrayList<CodeBlock> = getBlocksByType(CodeBlock.BlockType.Class)

    /**
     * Return functions of JAVA file
     */
    fun getFunctions(): ArrayList<CodeBlock> = getBlocksByType(CodeBlock.BlockType.Function)

    /**
     * Return code blocks from type
     */
    fun getBlocksByType(type: CodeBlock.BlockType): ArrayList<CodeBlock> =
        getBlocksByType(arrayOf(type), this.rootCodeBlocks)

    /**
     * Return code blocks from types
     */
    fun getBlocksByTypes(types: Array<CodeBlock.BlockType>): ArrayList<CodeBlock> =
        getBlocksByType(types, this.rootCodeBlocks)

    /**
     * Return code blocks from type (recursive)
     */
    private fun getBlocksByType(types: Array<CodeBlock.BlockType>, blocks: ArrayList<CodeBlock>?): ArrayList<CodeBlock> {
        val result = ArrayList<CodeBlock>()
        if (blocks == null) return result
        for (block in blocks) {
            if (Utils.arrayContains(types, block.type)) {
                result.add(block)
            }
            if (Utils.arrayNotEmpty(block.subBlocks)) {
                val r = getBlocksByType(types, block.subBlocks)
                if (Utils.arrayNotEmpty(r)) result.addAll(r)
            }
        }
        return result
    }

    /**
     * Add a package name import.
     */
    fun addImport(packageName: String) {
        val code = "\nimport " + packageName.trim() + ";"

        val javaParser = JavaParser()
        val javaCode = javaParser.parse(code)
        val imports = javaCode.getImports()
        if (imports.isEmpty()) return
        val blockImport = imports[0]

        if (!addBlockAtPosition(this.rootCodeBlocks, blockImport, InsertPosition.AtTheEnd, CodeBlock.BlockType.Import)) {
            addBlockAtPosition(this.rootCodeBlocks, blockImport, InsertPosition.RightAfter, CodeBlock.BlockType.Package)
        }
    }

    /**
     * Add an attribute in the class (or an interface)
     */
    fun addAttribute(attributeCode: String): Boolean = addAttribute(attributeCode, null)

    /**
     * Add an attribute in the class
     */
    fun addAttribute(attributeCode: String, className: String?): Boolean {
        var blockClass: CodeBlock? = null
        val types = arrayOf(CodeBlock.BlockType.Class, CodeBlock.BlockType.Interface)
        for (block in getBlocksByTypes(types)) {
            if (className == null || className == block.name)
                blockClass = block
        }
        if (blockClass == null) return false

        val javaParser = JavaParser()
        val javaCode = javaParser.parse("\n\n    " + attributeCode.trim())
        val allBlocks = javaCode.getAllBlocks()
        if (allBlocks.isEmpty()) return false
        val block = allBlocks[0]
        block.hasParent = true
        block.parentType = blockClass.type

        if (!addBlockAtPosition(blockClass.subBlocks, block, InsertPosition.AtTheEnd, CodeBlock.BlockType.Attribute)) {
            addBlockAtFirst(blockClass.subBlocks, block)
        }
        return true
    }

    /**
     * Add a function into the default class
     */
    fun addFunction(functionCode: String): Boolean = addFunction(functionCode, null)

    /**
     * Add a function into a specific class
     */
    fun addFunction(functionCode: String, className: String?): Boolean {
        var blockClass: CodeBlock? = null
        val types = arrayOf(CodeBlock.BlockType.Class, CodeBlock.BlockType.Interface)
        for (block in getBlocksByTypes(types)) {
            if (className == null || className == block.name)
                blockClass = block
        }
        if (blockClass == null) return false

        val javaParser = JavaParser()
        val javaCode = javaParser.parse("\n\n    " + functionCode.trim())
        val allBlocks = javaCode.getAllBlocks()
        if (allBlocks.isEmpty()) return false
        val block = allBlocks[0]
        block.hasParent = true
        block.type = CodeBlock.BlockType.Function
        block.parentType = blockClass.type

        addBlockAtTheEnd(blockClass.subBlocks, block)
        return true
    }

    /**
     * Insert positions for block of code
     */
    private enum class InsertPosition {
        JustBefore,
        RightAfter,
        AtTheEnd
    }

    /**
     * Add a block of code at the first index into the blocks list
     */
    private fun addBlockAtFirst(blocks: ArrayList<CodeBlock>, newBlock: CodeBlock) {
        newBlock.injected = true

        if (blocks.size > 0) {
            if (UPDATE_OFFSETS) {
                val firstBlock = blocks[0]
                newBlock.start = 0
                newBlock.end = newBlock.code.length
                newBlock.offset = firstBlock.offset
                newBlock.innerOffset = firstBlock.innerOffset
            }

            Utils.insertInArray(blocks, 0, newBlock)

            if (UPDATE_OFFSETS) {
                for (i in 1 until blocks.size) {
                    // Shift block offsets:
                    val block = blocks[i]
                    block.start = block.start + newBlock.code.length
                    block.end = block.end + newBlock.code.length
                }
            }
        } else {
            if (UPDATE_OFFSETS) {
                newBlock.start = 0
                newBlock.end = newBlock.code.length
            }
            Utils.insertInArray(blocks, 0, newBlock)
        }
    }

    /**
     * Add a block of code at the end of blocks list
     */
    private fun addBlockAtTheEnd(blocks: ArrayList<CodeBlock>, newBlock: CodeBlock) {
        newBlock.injected = true
        if (UPDATE_OFFSETS) {
            if (blocks.size > 0) {
                val lastBlock = blocks[blocks.size - 1]
                newBlock.updatePosition(lastBlock)
            } else {
                newBlock.end = newBlock.code.length
            }
        }
        blocks.add(newBlock)
    }

    /**
     * Add a block of code into the entire code.
     */
    private fun addBlockAtPosition(
        blocks: ArrayList<CodeBlock>,
        newBlock: CodeBlock,
        position: InsertPosition,
        type: CodeBlock.BlockType
    ): Boolean {
        var inserted = false
        newBlock.injected = true

        if (position == InsertPosition.AtTheEnd) {
            var i = blocks.size - 1
            while (i > 0) {

                val block = blocks[i]

                // Insert new block ar right position:
                if (!inserted && block.type == type) {
                    if (UPDATE_OFFSETS) newBlock.updatePosition(block)
                    Utils.insertInArray(blocks, i + 1, newBlock)
                    if (!UPDATE_OFFSETS) return true
                    i++
                    inserted = true
                } else if (inserted) {
                    // Shift block offsets:
                    block.start = block.start + newBlock.code.length
                    block.end = block.end + newBlock.code.length
                }
                i--
            }
        } else {
            var i = 0
            while (i < blocks.size) {

                val block = blocks[i]

                // Insert new block at right position:
                if (!inserted && block.type == type) {
                    if (position == InsertPosition.JustBefore) {
                        if (UPDATE_OFFSETS) newBlock.updatePosition(block)
                        Utils.insertInArray(blocks, i, newBlock)
                        if (!UPDATE_OFFSETS) return true
                        inserted = true
                    } else if (position == InsertPosition.RightAfter) {
                        if (UPDATE_OFFSETS) newBlock.updatePosition(block)
                        Utils.insertInArray(blocks, i + 1, newBlock)
                        if (!UPDATE_OFFSETS) return true
                        i++
                        inserted = true
                    }
                } else if (inserted) {
                    // Shift block offsets:
                    block.start = block.start + newBlock.code.length
                    block.end = block.end + newBlock.code.length
                }
                i++
            }
        }

        return inserted
    }

    /**
     * Inject fake code: fake attribute
     * Important: only if a class exists into the JAVA code
     */
    fun injectFakeKeys() {
        val fakeParamName = TextUtils.getRandomString(10, TextUtils.PARAM_CHARACTERS)

        val sizeValue = Utils.getRandomNumberInRange(10, 30)
        val randomValue = TextUtils.getRandomString(sizeValue, TextUtils.KEY_CHARACTERS)

        injectFakeKeys(fakeParamName, randomValue)
    }

    /**
     * Inject fake code: fake attribute
     * Important: only if a class exists into the JAVA code
     */
    fun injectFakeKeys(fakeParamName: String, randomValue: String) {
        val classBlocks = getBlocksByType(CodeBlock.BlockType.Class)
        val functions = getFunctions()

        if (Utils.arrayNotEmpty(classBlocks) && Utils.arrayNotEmpty(functions)) {

            // Generate fake code:
            val fakeAttribute = getFakeAttribute(fakeParamName, randomValue)
            val fakeCode = getFakeCode(fakeParamName)

            // Inject attribute:
            addAttribute(fakeAttribute)

            // Search a function where inject fake code:
            for (blockFunction in functions) {

                if (blockFunction.hasChildren()) {

                    val lastLineOfCode = blockFunction.subBlocks.last()
                    if (lastLineOfCode.type == CodeBlock.BlockType.Return) {
                        addBlockAtFirst(blockFunction.subBlocks, fakeCode)
                    } else {
                        addBlockAtTheEnd(blockFunction.subBlocks, fakeCode)
                    }
                    break
                }
            }
        }
    }

    /**
     * Inject fake code (Kotlin syntax): fake attribute + usage line,
     * so the honeypot survives ProGuard in Kotlin classes too.
     * Important: only if a class exists into the Kotlin code
     */
    fun injectFakeKeysKotlin() {
        val fakeParamName = TextUtils.getRandomString(10, TextUtils.PARAM_CHARACTERS)
        val sizeValue = Utils.getRandomNumberInRange(10, 30)
        val randomValue = TextUtils.getRandomString(sizeValue, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

        injectFakeKeysKotlin(fakeParamName, randomValue)
    }

    /**
     * Inject fake code (Kotlin syntax): fake attribute + usage line
     * Important: only if a class exists into the Kotlin code
     */
    fun injectFakeKeysKotlin(fakeParamName: String, randomValue: String) {
        val classBlocks = getBlocksByType(CodeBlock.BlockType.Class)

        if (Utils.arrayNotEmpty(classBlocks)) {
            val targetClass = classBlocks.firstOrNull {
                it.type == CodeBlock.BlockType.Class &&
                it.hasChildren() &&
                !it.code.contains("enum class") &&
                !it.code.contains("interface") &&
                !it.code.contains("annotation class") &&
                !it.code.contains("sealed interface")
            }
            if (targetClass != null) {
                val fakeAttribute = "private val $fakeParamName = \"$randomValue\"\n"
                val javaParser = JavaParser()
                val fakeAttrCode = javaParser.parse("\n\n    " + fakeAttribute.trim())
                val blocks = fakeAttrCode.getAllBlocks()
                if (blocks.isNotEmpty()) {
                    val fakeAttrBlock = blocks[0]
                    fakeAttrBlock.hasParent = true
                    addBlockAtTheEnd(targetClass.subBlocks, fakeAttrBlock)
                }
            }
        }
    }

    /**
     * Generate a fake Java code which is calling the fake attribute (avoid code clean by ProGuard)
     */
    private fun getFakeCode(paramName: String): CodeBlock {
        val code = "\n        if ($paramName.isEmpty()) $paramName.getClass().toString();"
        val javaParser = JavaParser()
        val javaCode = javaParser.parse(code)
        val block = javaCode.getAllBlocks()[0]
        block.hasParent = true
        return block
    }

    /**
     * Generate a fake Java attribute
     */
    private fun getFakeAttribute(paramName: String, randomValue: String): String {
        return "public static final String $paramName = \"$randomValue\";"
    }

    /**
     * Encrypt all string values
     */
    @Throws(Exception::class)
    fun encryptStrings(key: String, functionName: String, isKotlin: Boolean = false) {

        var stringOffset = 0
        var block: CodeBlock? = null

        // For each String value:
        for (cs in getStringValues()) {

            // Escape switch/case value (not authorized by Java compiler):
            if (cs.isCaseValue) continue

            // Kotlin: string templates, raw strings, const val, and annotations must stay literal:
            if (isKotlin && isSkippedKotlinString(cs.value, cs)) continue

            // Search the code block which contains the string value:
            if (block == null || !(block.startWithOffset() <= cs.start && cs.end <= block.endWithOffset())) {
                block = getBlockBetween(this.rootCodeBlocks, cs.start, cs.end)
                stringOffset = 0
            }

            // If code block found:
            if (block != null) {
                val bStart = cs.start - (block.start + block.offset)
                val bEnd = cs.end - (block.start + block.offset)

                // Get the string value en encrypt it:
                val value = cs.value.substring(1, cs.value.length - 1)
                val encrypted = encryptString(value, key, functionName, null, isKotlin)

                // Inject the Enigma signature function:
                val builder = StringBuilder()
                builder.append(block.code, 0, bStart + stringOffset)
                builder.append(encrypted)
                builder.append(block.code, bEnd + stringOffset, block.code.length)

                // Compute the length difference due to the code modification:
                val originalLength = cs.value.length
                val encryptedLength = encrypted.length
                val lengthDiff = encryptedLength - originalLength

                block.code = builder.toString()

                stringOffset += lengthDiff
            }
        }
    }

    /**
     * Encrypt all string values (Kotlin mode: Kotlin cipher style, skips
     * raw triple-quoted strings and $ string templates which must stay literal)
     */
    @Throws(Exception::class)
    fun encryptStringsKotlin(key: String, functionName: String) {
        encryptStrings(key, functionName, true)
    }

    /**
     * Obfuscate string values using MaskWeb-style keyword masking (e.g. "PHRASE".lowercase()).
     * Returns the set of used keywords so corresponding imports can be injected.
     */
    @Throws(Exception::class)
    fun maskStringsKotlin(
        profile: String,
        saltHex: String,
        keywords: List<String>,
        globalMapping: MutableMap<String, Pair<String, String>>,
        wordCount: Int = 5,
        customWords: List<String>? = null
    ): Set<String> {
        val usedKeywords = HashSet<String>()
        val valueToMask = HashMap<String, Pair<String, String>>()
        var stringOffset = 0
        var block: CodeBlock? = null

        val kwList = if (keywords.isNotEmpty()) keywords else listOf("lowercase")

        for (cs in getStringValues()) {
            if (cs.isCaseValue) continue
            if (isSkippedKotlinString(cs.value, cs)) continue

            if (block == null || !(block.startWithOffset() <= cs.start && cs.end <= block.endWithOffset())) {
                block = getBlockBetween(this.rootCodeBlocks, cs.start, cs.end)
                stringOffset = 0
            }

            if (block != null) {
                val bStart = cs.start - (block.start + block.offset)
                val bEnd = cs.end - (block.start + block.offset)

                var rawValue = cs.value.substring(1, cs.value.length - 1)
                rawValue = rawValue.replace("\\\"", "\"").replace("\\\\", "\\")

                val (masked, kw) = valueToMask.getOrPut(rawValue) {
                    val k = kwList[(valueToMask.size + globalMapping.size) % kwList.size]
                    var m = MaskUtils.styledMask(wordCount, k, customWords)
                    while (globalMapping.containsKey(m)) {
                        m = MaskUtils.styledMask(wordCount, k, customWords)
                    }
                    val token = MaskUtils.encryptToken(profile, saltHex, rawValue)
                    globalMapping[m] = Pair(k, token)
                    Pair(m, k)
                }

                usedKeywords.add(kw)
                val replacement = "\"$masked\".$kw()"

                val builder = StringBuilder()
                builder.append(block.code, 0, bStart + stringOffset)
                builder.append(replacement)
                builder.append(block.code, bEnd + stringOffset, block.code.length)

                val lengthDiff = replacement.length - cs.value.length
                block.code = builder.toString()
                stringOffset += lengthDiff
            }
        }

        return usedKeywords
    }

    /**
     * Check if a Kotlin string literal must stay unencrypted.
     * Skips raw triple-quoted fragments, $ string templates (interpolation
     * would break), const val/var declarations, and annotations.
     */
    private fun isSkippedKotlinString(quoted: String?, cs: CodeString): Boolean {
        if (quoted == null || quoted.length < 2) return true
        if (quoted.contains("\"\"\"")) return true
        val value = quoted.substring(1, quoted.length - 1)
        if (value.contains("$")) return true

        // Check if string is part of a const val/var, annotation, or nested in string template
        val start = cs.start
        if (start > 0 && start <= sourceCode.length) {
            val lookbackStart = maxOf(0, start - 400)
            val lookback = sourceCode.substring(lookbackStart, start)

            // String template interpolation check: nested inside unclosed ${ ... "str" ... }
            val lastDollarBrace = lookback.lastIndexOf("\${")
            if (lastDollarBrace != -1) {
                val afterDollarBrace = lookback.substring(lastDollarBrace)
                val opens = afterDollarBrace.count { it == '{' }
                val closes = afterDollarBrace.count { it == '}' }
                if (opens > closes) {
                    return true
                }
            }

            // Const val check: lookback up to 400 chars for const val / const var before the string
            val lastConstVal = lookback.lastIndexOf("const val")
            val lastConstVar = lookback.lastIndexOf("const var")
            val lastConst = maxOf(lastConstVal, lastConstVar)
            if (lastConst != -1) {
                val afterConst = lookback.substring(lastConst)
                if (!afterConst.contains(";\n") && !afterConst.contains("\n\n") && !afterConst.contains("\n    val ") && !afterConst.contains("\n    fun ")) {
                    return true
                }
            }

            // Annotation parameter check: unclosed @Annotation( ...
            val lastAt = lookback.lastIndexOf('@')
            if (lastAt != -1) {
                val afterAt = lookback.substring(lastAt)
                val opens = afterAt.count { it == '(' }
                val closes = afterAt.count { it == ')' }
                if (opens > closes) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Encrypt a string value, to Enigma ciphering style
     */
    @Throws(Exception::class)
    private fun encryptString(
        value: String,
        key: String,
        functionName: String,
        encryptTask: DefaultTask?,
        isKotlin: Boolean = false
    ): String {
        val builder = StringBuilder()
        var v = value.replace("\\\"", "\"")
        v = v.replace("\\\\", "\\")

        val encrypted = encryptString(key, v, encryptTask)

        if (encryptTask == null) {
            val enc = AESUtils.toByte(encrypted)
            builder.append(functionName)
            if (isKotlin) {
                builder.append("(byteArrayOf")
                builder.append(bytesToKotlinCode(enc))
                builder.append(")")
            } else {
                builder.append("(new byte[]")
                builder.append(bytesToCode(enc))
                builder.append(")")
            }
        } else {
            builder.append(functionName)
            builder.append("(\"")
            builder.append(encrypted)
            builder.append("\")")
        }

        return builder.toString()
    }

    /**
     * Encrypt string value
     */
    @Throws(Exception::class)
    private fun encryptString(key: String, value: String, encryptTask: DefaultTask?): String {
        if (encryptTask == null) {
            if (key == null) throw Exception("Hash Key undefined!")
            return AESUtils.encrypt(key, value)
        } else {
            val encryptMethod = encryptTask.javaClass.getMethod("encrypt", String::class.java)
            return encryptMethod.invoke(encryptTask, value) as String
        }
    }

    /**
     * Convert bytes value, to JAVA code format
     */
    private fun bytesToCode(bytes: ByteArray): String {
        val builder = StringBuilder()
        builder.append("{")
        for (i in bytes.indices) {
            val b = bytes[i]
            builder.append(b)
            if (i < bytes.size - 1) builder.append(", ")
        }
        builder.append("}")
        return builder.toString()
    }

    /**
     * Convert bytes value, to Kotlin code format (byteArrayOf paremeters)
     */
    private fun bytesToKotlinCode(bytes: ByteArray): String {
        val builder = StringBuilder()
        builder.append("(")
        for (i in bytes.indices) {
            val b = bytes[i]
            builder.append(b)
            if (i < bytes.size - 1) builder.append(", ")
        }
        builder.append(")")
        return builder.toString()
    }

    /**
     * Source code formatted
     */
    fun toCode(): String {
        val sb = StringBuilder()
        for (block in getAllBlocks()) {
            if (!block.hasParent) sb.append(block.toCode())
        }
        // End Of File
        sb.append("\n")
        return sb.toString()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (block in this.rootCodeBlocks) {
            sb.append(block.code)
        }
        return sb.toString()
    }

    companion object {
        /**
         * Enable or disable the position/offset update mechanism.
         */
        const val UPDATE_OFFSETS = false
    }
}
