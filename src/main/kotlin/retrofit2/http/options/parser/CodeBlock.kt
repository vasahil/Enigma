package retrofit2.http.options.parser

import retrofit2.http.options.utils.SmartArrayList
import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils

/**
 * Represent a block of JAVA code
 */
class CodeBlock {

    /**
     * Indicate the star position of block in the source code
     */
    var start = -1

    /**
     * Indicate the end position of block in the source code
     */
    var end = -1

    /**
     * Start/End offset (from start of file)
     */
    var offset = 0

    /**
     * Inner offset (from start of parent)
     */
    var innerOffset = 0

    /**
     * Source code
     */
    lateinit var code: String

    /**
     * Black name for Class or function or Annotation
     */
    var name: String? = null

    /**
     * Define type of block
     */
    var type = BlockType.Undefined

    /**
     * define modifier type (public, private, protected)
     */
    var modifier: Modifier? = null

    /**
     * Type name of the object returned by the function (optional)
     */
    var returnType: String? = null

    /**
     * Sub blocks of code contains by the current block
     */
    var subBlocks = SmartArrayList<CodeBlock>()

    /**
     * Source code of the block split in words (useful for processing)
     */
    var words = ArrayList<CodeString>()

    /**
     * Indicate if the code block is nested or not.
     */
    var hasParent = false

    /**
     * Sub block position indexes
     */
    var subIndexes: SmartArrayList<CodePosition>? = null

    /**
     * Type of the parent block
     */
    var parentType = BlockType.Undefined

    /**
     * Indicate if the code block is injected by programming code.
     */
    var injected = false

    /**
     * Modifiers (for class attributes and functions)
     */
    enum class Modifier {
        Public,
        Private,
        Protected
    }

    /**
     * All JAVA types of code blocks
     */
    enum class BlockType {
        Undefined,
        Package,
        Import,
        Class,
        AnonymousInnerClass,
        Interface,
        Attribute,
        CommentLine,
        CommentBlock,
        Annotation,
        Function,
        Constructor,
        Condition,
        Loop,
        TryCatch,
        LineOfCode,
        Return,
        StringValue
    }

    /**
     * Return the start position (character index) with offset
     */
    fun startWithOffset(): Int = start + offset

    /**
     * Return the end position (character index) with offset
     */
    fun endWithOffset(): Int = end + offset

    /**
     * Convert all words to a String value (source code)
     */
    fun wordsToString(): String {
        val sb = StringBuilder()
        for (s in words) sb.append(s.value)
        return sb.toString()
    }

    /**
     * Update the offset and position of the block from the previous block of the list.
     */
    fun updatePosition(previousBlock: CodeBlock) {
        this.start = previousBlock.end
        this.end = this.start + this.code.length
        this.offset = previousBlock.offset
        this.innerOffset = previousBlock.innerOffset
    }

    /**
     * Indicate if the block is a line or block of comment
     */
    fun isComment(): Boolean = isComment(type)

    /**
     * Indicate if the block is a Function or a Class
     */
    fun isFunctionOrClass(blockType: BlockType): Boolean =
        blockType == BlockType.Class || blockType == BlockType.Function

    /**
     * Indicate if the block has children or not
     */
    fun hasChildren(): Boolean = Utils.arrayNotEmpty(subBlocks)

    /**
     * Return the first sub block none injected by programming
     */
    private fun getFirstNoneInjectedBlock(): CodeBlock? {
        if (Utils.arrayNotEmpty(this.subBlocks)) {
            for (i in 0 until this.subBlocks.size) {
                val block = this.subBlocks[i]
                if (!block.injected) return block
            }
        }
        return null
    }

    /**
     * Return the latest sub block none injected by programming
     */
    private fun getLastNoneInjectedBlock(): CodeBlock? {
        if (Utils.arrayNotEmpty(this.subBlocks)) {
            for (i in this.subBlocks.size - 1 downTo 0) {
                val block = this.subBlocks[i]
                if (!block.injected) return block
            }
        }
        return null
    }

    /**
     * Generate the source code of the block
     */
    fun toCode(): String = toCode(0)

    /**
     * Generate the source code of the block
     */
    private fun toCode(level: Int): String {
        val sb = StringBuilder()

        if (!hasChildren()) {
            sb.append(code)
        } else {

            // Add signature code:
            val firstBlock = getFirstNoneInjectedBlock()
            if (firstBlock != null) {
                val prefixEnd = firstBlock.innerOffset + firstBlock.start
                val prefix = TextUtils.safeSubstring(code, 0, prefixEnd)
                if (prefix != null) sb.append(prefix)
            }

            // For each sub block:
            for (i in 0 until subBlocks.size) {
                val subBlock = subBlocks[i]
                val nextSubBlock = subBlocks.next(i)

                // Add sub code:
                sb.append(subBlock.toCode(level + 1))

                // Add code between current sub block and next one:
                if (!subBlock.injected && i < subBlocks.lastIndex()) {
                    val suffixStart = subBlock.innerOffset + subBlock.end
                    val suffixEnd = nextSubBlock?.innerOffset?.plus(nextSubBlock.start) ?: code.length
                    val suffix = TextUtils.safeSubstring(code, suffixStart, suffixEnd)
                    if (suffix != null) sb.append(suffix)
                }
            }

            // Close the block:
            val latestBlock = getLastNoneInjectedBlock()
            if (latestBlock != null) {
                val suffixStart = latestBlock.innerOffset + latestBlock.end
                val suffixEnd = code.length
                val suffix = TextUtils.safeSubstring(code, suffixStart, suffixEnd)
                if (suffix != null) sb.append(suffix)
            }
        }

        return sb.toString()
    }

    /**
     * Return a String version of the object
     */
    override fun toString(): String {
        val m = if (modifier != null) Modifier.Public.toString() + ": " else ""
        return "[$m" + (start + offset) + ", " + (end + offset) + "] ($type:$name) " + wordsToString()
    }

    companion object {
        /**
         * Indicate if the block is a line or block of comment
         */
        fun isComment(blockType: BlockType): Boolean =
            blockType == BlockType.CommentBlock || blockType == BlockType.CommentLine

        /**
         * Indicate if the block is a Class or Anonymous Inner Class
         */
        fun isClass(blockType: BlockType): Boolean =
            blockType == BlockType.Class || blockType == BlockType.AnonymousInnerClass
    }
}
