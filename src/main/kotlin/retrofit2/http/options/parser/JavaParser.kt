package retrofit2.http.options.parser

import retrofit2.http.options.utils.SmartArrayList
import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils

/**
 * JAVA Simple Parser: parse JAVA source code and detects String values
 */
class JavaParser {

    /**
     * Parse JAVA File
     */
    fun parse(sourceCode: String): JavaCode {
        val strings = ArrayList<CodeString>()
        val blocks = this.parse(sourceCode, null, strings, 0)
        return JavaCode(blocks, strings, sourceCode)
    }

    /**
     * Parse JAVA File
     */
    private fun parse(
        source: String,
        parent: CodeBlock?,
        strings: ArrayList<CodeString>?,
        offset: Int
    ): ArrayList<CodeBlock> {

        val blocks = ArrayList<CodeBlock>()

        var counterCurlyBrackets = 0
        var counterParenthesis = 0
        var counterBrackets = 0
        var counterAnnotationParenthesis = 0

        var currentBlock = CodeBlock.BlockType.Undefined

        var word: CodeString? = null
        var string: CodeString? = null
        var block: CodeBlock? = null

        for (i in source.indices) {

            val prevChar: Char = if (i > 0) source[i - 1] else ' '
            val curChar: Char = source[i]
            val nextChar: Char = if (i < source.length - 1) source[i + 1] else ' '
            val nextNoneEmptyChar = getNextNoneEmptyChar(source, i + 1)

            // Track String values and comments blocks:
            if (currentBlock == CodeBlock.BlockType.Undefined) {
                if (curChar == cDoubleQuote && !isEscaped(source, i) && !isCharLiteralQuote(source, i)) {
                    currentBlock = CodeBlock.BlockType.StringValue
                } else if (curChar == cSlash && nextChar == cSlash) {
                    currentBlock = CodeBlock.BlockType.CommentLine
                } else if (curChar == cSlash && nextChar == cStar) {
                    currentBlock = CodeBlock.BlockType.CommentBlock
                } else if (curChar == cAnnotation && counterAnnotationParenthesis == 0 && word == null) {
                    currentBlock = CodeBlock.BlockType.Annotation
                }
            } else if (currentBlock == CodeBlock.BlockType.StringValue) {
                if (curChar == cDoubleQuote && !isEscaped(source, i))
                    currentBlock = CodeBlock.BlockType.Undefined
            }

            // Brackets & parenthesis counters:
            if (currentBlock != CodeBlock.BlockType.CommentLine && currentBlock != CodeBlock.BlockType.CommentBlock) {
                if (curChar == cCurlyBracketOpen) counterCurlyBrackets++
                if (curChar == cCurlyBracketClose) counterCurlyBrackets--
                if (curChar == cParenthesisOpen) counterParenthesis++
                if (curChar == cParenthesisClose) counterParenthesis--
                if (curChar == cBracketOpen) counterBrackets++
                if (curChar == cBracketClose) counterBrackets--
                if (currentBlock == CodeBlock.BlockType.Annotation) {
                    if (curChar == cParenthesisOpen) counterAnnotationParenthesis++
                    if (curChar == cParenthesisClose) counterAnnotationParenthesis--
                }
            }

            // String value detection
            if (parent == null && strings != null) {
                if (currentBlock == CodeBlock.BlockType.StringValue && string == null) {
                    string = CodeString(i)
                } else if (string != null && currentBlock != CodeBlock.BlockType.StringValue) {
                    string.end = i + 1
                    string.value = source.substring(string.start, string.end)
                    string.isCaseValue = block != null && isSwitchCaseValue(block.words, string.value, nextNoneEmptyChar)
                    strings.add(string)
                    string = null
                }
            }

            // Start new word:
            if (word == null && !TextUtils.inCharactersList(charBreaks, curChar)) {
                word = CodeString(i)
            }
            if (TextUtils.isEmptyChar(curChar) || TextUtils.inCharactersList(charBreaks, curChar) ||
                isEndBlockComment(currentBlock, curChar, prevChar)
            ) {

                // End of current word, then create word object:
                if (word != null) {
                    word.end = i
                    word.value = source.substring(word.start, word.end)
                    if (currentBlock != CodeBlock.BlockType.StringValue &&
                        currentBlock != CodeBlock.BlockType.CommentBlock &&
                        currentBlock != CodeBlock.BlockType.CommentLine
                    ) {
                        word.isInstruction = isInstruction(word.value)
                        word.isType = isType(word.value)
                    }

                    // New Block detection:
                    if (block == null) {
                        block = CodeBlock()
                        block.offset = offset
                        if (parent != null) {
                            block.hasParent = true
                            block.parentType = parent.type
                        }
                        block.start = i - word.value.length
                    }

                    // Add word to current block:
                    if (word.value.isNotEmpty()) block.words.add(word)
                }

                // Start new word:
                word = CodeString(i)
                word.end = i
                word.value = curChar.toString()

                // Add words to current block:
                if (block != null && word.value.isNotEmpty()) block.words.add(word)
                word = null
            }

            // Detect END of block
            if (block != null) {

                if ((
                    isEndOfCodeBlock(currentBlock, curChar, nextNoneEmptyChar) ||
                        isEndAnnotation(currentBlock, curChar, nextNoneEmptyChar, counterParenthesis) ||
                        isEndCommentLine(currentBlock, curChar) ||
                        isEndBlockComment(currentBlock, curChar, prevChar)
                    ) && counterCurlyBrackets == 0 && counterParenthesis == 0 && counterBrackets == 0
                ) {

                    block.end = i + 1
                    block.code = source.substring(block.start, block.end)

                    // Analyze sub source code:
                    if (!CodeBlock.isComment(currentBlock))
                        block.subIndexes = getSubBlockIndexes(block)

                    if (Utils.arrayNotEmpty(block.subIndexes)) {

                        for (subIndexes in block.subIndexes!!) {

                            // Safe substring: important if method doesn't contains any sub code
                            val subCode = TextUtils.safeSubstring(block.code, subIndexes.start, subIndexes.end)

                            // Set block type:
                            block.type = getBlockType(block, true)
                            // Search block name and properties:
                            parseBlockProperties(block)

                            // Compute the sub block offset (chars index):
                            val subBlockOffset = block.start + block.offset + subIndexes.start

                            // Parse the sub block:
                            if (subCode != null) {
                                val subBlocks = this.parse(subCode, block, null, subBlockOffset)
                                for (subBlock in subBlocks) {
                                    subBlock.innerOffset = subIndexes.start
                                }
                                block.subBlocks.addAll(subBlocks)
                            } else {
                                // If no sub code, then remove sub indexes:
                                block.subIndexes = null
                            }
                        }

                    } else {
                        // Set block type:
                        block.type = getBlockType(block, false)
                        // Search block name and properties:
                        parseBlockProperties(block)
                    }

                    // Detect 'constructor':
                    if (block.type == CodeBlock.BlockType.Function && parent != null &&
                        parent.type == CodeBlock.BlockType.Class &&
                        block.name != null && block.name == parent.name
                    ) {
                        block.type = CodeBlock.BlockType.Constructor
                    }

                    // Detect empty spaces (line breaks...)
                    if (blocks.size > 0) {
                        val lastBlock = blocks[blocks.size - 1]
                        val emptyBlock = CodeBlock()
                        emptyBlock.start = lastBlock.end
                        emptyBlock.end = block.start
                        emptyBlock.code = source.substring(emptyBlock.start, emptyBlock.end)
                        print(emptyBlock.code)
                    }

                    blocks.add(block)
                    block = null
                }
            }

            // Detect end block type:
            if (currentBlock == CodeBlock.BlockType.CommentLine) {
                if (TextUtils.isReturnChar(curChar))
                    currentBlock = CodeBlock.BlockType.Undefined
            } else if (currentBlock == CodeBlock.BlockType.CommentBlock) {
                if (curChar == cSlash && prevChar == cStar)
                    currentBlock = CodeBlock.BlockType.Undefined
            } else if (currentBlock == CodeBlock.BlockType.Annotation) {
                if (isEndAnnotation(currentBlock, curChar, nextNoneEmptyChar, counterAnnotationParenthesis))
                    currentBlock = CodeBlock.BlockType.Undefined
            }
        } // End for loop

        // End of file: close a trailing block which never met ';' or '}'.
        // Happens with Kotlin constructs ending with ')' (data class without
        // body) or a trailing comment. Without this the tail is silently dropped.
        // Whitespace-only tails stay dropped: spacing is re-emitted via suffix logic.
        if (block != null && block.wordsToString().trim().isNotEmpty()) {
            block.end = source.length
            block.code = source.substring(block.start, block.end)
            block.type = getBlockType(block, false)
            parseBlockProperties(block)
            blocks.add(block)
        }

        return blocks
    }

    private fun getSubBlockIndexes(block: CodeBlock): SmartArrayList<CodePosition> {
        val indexes = SmartArrayList<CodePosition>()
        var counterCurlyBracket = 0
        var previousNoneEmptyChar = ' '
        for (j in block.code.indices) {

            val c = block.code[j]

            if (c == cCurlyBracketOpen) counterCurlyBracket++
            if (c == cCurlyBracketClose) counterCurlyBracket--

            if (c == cCurlyBracketOpen && counterCurlyBracket == 1 && previousNoneEmptyChar != cBracketClose) {
                indexes.add(CodePosition(j + 1))
            } else if (c == cCurlyBracketClose && counterCurlyBracket == 0 && indexes.size > 0) {
                indexes[indexes.size - 1].end = j - 1
            }

            if (!TextUtils.isEmptyChar(c)) previousNoneEmptyChar = c
        }
        return indexes
    }

    /**
     * Detect end of Line of code, End of Class, Function, Condition, Loop...
     */
    private fun isEndOfCodeBlock(
        currentBlock: CodeBlock.BlockType,
        curChar: Char,
        nextNoneEmptyChar: Char
    ): Boolean {
        return !CodeBlock.isComment(currentBlock) &&
            currentBlock != CodeBlock.BlockType.StringValue &&
            (
                curChar == cSemicolon ||
                    curChar == cCurlyBracketClose && nextNoneEmptyChar != cSemicolon
                )
    }

    /**
     * Detect end of block annotation
     */
    private fun isEndAnnotation(
        currentBlock: CodeBlock.BlockType,
        curChar: Char,
        nextNoneEmptyChar: Char,
        parenthesisCounter: Int
    ): Boolean {
        if (currentBlock == CodeBlock.BlockType.Annotation && parenthesisCounter == 0) {
            if (curChar == cParenthesisClose ||
                TextUtils.isEmptyChar(curChar) && Character.isAlphabetic(nextNoneEmptyChar.code)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Detect end of line of Comment
     */
    private fun isEndCommentLine(currentBlock: CodeBlock.BlockType, curChar: Char): Boolean {
        return currentBlock == CodeBlock.BlockType.CommentLine && TextUtils.isReturnChar(curChar)
    }

    /**
     * Detect end of block Comment
     */
    private fun isEndBlockComment(
        currentBlock: CodeBlock.BlockType,
        curChar: Char,
        prevChar: Char
    ): Boolean {
        return currentBlock == CodeBlock.BlockType.CommentBlock && curChar == cSlash && prevChar == cStar
    }

    private fun isEscaped(source: String, index: Int): Boolean {
        var count = 0
        var i = index - 1
        while (i >= 0 && source[i] == cEscape) {
            count++
            i--
        }
        return count % 2 == 1
    }

    private fun isCharLiteralQuote(source: String, index: Int): Boolean {
        if (index > 0 && source[index - 1] == '\'') {
            if (index < source.length - 1 && source[index + 1] == '\'') return true
        }
        return false
    }

    /**
     * Return the none empty character
     */
    private fun getNextNoneEmptyChar(data: String, index: Int): Char {
        var i = index
        var c = ' '
        while (i < data.length) {
            if (!TextUtils.isEmptyChar(data[i])) {
                c = data[i]
                break
            }
            i++
        }
        return c
    }

    /**
     * Return True is the keyword is a native Java type
     */
    private fun isType(word: String): Boolean {
        for (t in types) {
            if (t == word) return true
        }
        return false
    }

    /**
     * Check if the string value is a switch/case value condition, example: case "RoundedSquare":
     */
    private fun isSwitchCaseValue(words: ArrayList<CodeString>, value: String, nextNoneEmptyChar: Char): Boolean {
        val sDoubleQuote = cDoubleQuote.toString()
        if (nextNoneEmptyChar != cColon || !value.startsWith(sDoubleQuote) || !value.endsWith(sDoubleQuote)) return false

        var prevNonEmptyWord: CodeString? = null
        for (i in words.size - 1 downTo 1) {
            prevNonEmptyWord = words[i]
            val v = prevNonEmptyWord.value
            if (v.isNotEmpty() && !TextUtils.isSpace(v) && !TextUtils.isEqualsToChar(v, cDoubleQuote)) break
        }

        if (prevNonEmptyWord == null) return false
        return sCase == prevNonEmptyWord.value
    }

    private fun parseBlockProperties(block: CodeBlock) {
        if (block.type == CodeBlock.BlockType.Class || block.type == CodeBlock.BlockType.Interface) {
            parseClassName(block)
        } else if (block.type == CodeBlock.BlockType.Function) {
            parseFunction(block)
        } else if (block.type == CodeBlock.BlockType.Attribute) {
            parseModifier(block)
        } else if (block.type == CodeBlock.BlockType.Package || block.type == CodeBlock.BlockType.Import) {
            parsePackageOrImportName(block)
        } else if (block.type == CodeBlock.BlockType.Annotation) {
            parseAnnotation(block)
        }
    }

    private fun parseFunction(block: CodeBlock) {
        var typeName: String? = null
        for (word in block.words) {
            if (isBreakCharacter(word.value)) break
            parseModifier(block, word.value)
            if (!word.isInstruction && word.value.trim().isNotEmpty()) {
                if (typeName == null) {
                    typeName = word.value
                } else {
                    block.name = word.value
                    block.returnType = typeName
                    return
                }
            }
        }
        block.name = typeName
        block.returnType = null
    }

    private fun hasModifier(words: ArrayList<CodeString>?): Boolean {
        if (words == null) return false
        val firstWord = getFirstNoneEmptyWord(words) ?: return false
        return sPublic == firstWord.value || sPrivate == firstWord.value || sProtected == firstWord.value
    }

    private fun parseModifier(block: CodeBlock) {
        val firstWord = getFirstNoneEmptyWord(block.words)
        if (firstWord != null) parseModifier(block, firstWord.value)
    }

    private fun parseModifier(block: CodeBlock, word: String) {
        if (sPublic == word) block.modifier = CodeBlock.Modifier.Public
        if (sPrivate == word) block.modifier = CodeBlock.Modifier.Private
        if (sProtected == word) block.modifier = CodeBlock.Modifier.Protected
    }

    private fun parseClassName(block: CodeBlock) {
        for (word in block.words) {
            if (isBreakCharacter(word.value)) break
            parseModifier(block, word.value)
            if (!word.isInstruction && !word.isType && word.value.trim().isNotEmpty())
                block.name = word.value
        }
    }

    private fun isEmptyWord(word: CodeString): Boolean {
        return word.value.trim().isEmpty()
    }

    private fun getFirstNoneEmptyWord(words: ArrayList<CodeString>): CodeString? {
        for (word in words) {
            if (!isEmptyWord(word)) return word
        }
        return null
    }

    private fun getBlockType(block: CodeBlock, hasSubCode: Boolean): CodeBlock.BlockType {
        // has nested code (function, condition, class...)
        if (hasSubCode) {

            val firstWord = getFirstNoneEmptyWord(block.words) ?: return CodeBlock.BlockType.Undefined

            if (firstWord.value.startsWith(cAnnotation.toString())) return CodeBlock.BlockType.Annotation
            if (firstWord.value == sIf || firstWord.value == sElse || firstWord.value == sSwitch)
                return CodeBlock.BlockType.Condition
            if (firstWord.value == sFor || firstWord.value == sWhile) return CodeBlock.BlockType.Loop
            if (firstWord.value == sTry || firstWord.value == sCatch) return CodeBlock.BlockType.TryCatch

            for (word in block.words) {
                if (word.value == sClass) return CodeBlock.BlockType.Class
                if (word.value == sInterface) return CodeBlock.BlockType.Interface
            }

            if (!CodeBlock.isClass(block.parentType))
                return CodeBlock.BlockType.AnonymousInnerClass

            return CodeBlock.BlockType.Function
        } else {

            val firstWord = getFirstNoneEmptyWord(block.words) ?: return CodeBlock.BlockType.Undefined

            if (firstWord.value == sPackage) return CodeBlock.BlockType.Package
            if (firstWord.value == sImport) return CodeBlock.BlockType.Import
            if (firstWord.value.startsWith(cAnnotation.toString())) return CodeBlock.BlockType.Annotation
            if (firstWord.value == sIf || firstWord.value == sElse || firstWord.value == sSwitch)
                return CodeBlock.BlockType.Condition
            if (firstWord.value == sFor || firstWord.value == sWhile) return CodeBlock.BlockType.Loop
            if (firstWord.value == sReturn) return CodeBlock.BlockType.Return
            if (firstWord.value.startsWith(sBlockCommentStart)) return CodeBlock.BlockType.CommentBlock
            if (firstWord.value.startsWith(sLineComment)) return CodeBlock.BlockType.CommentLine

            for (word in block.words) {
                if (isEmptyWord(word)) continue
                if (word.value == sStatic) return CodeBlock.BlockType.Attribute
            }
        }

        return if (hasModifier(block.words)) CodeBlock.BlockType.Attribute else CodeBlock.BlockType.LineOfCode
    }

    companion object {
        private const val cCurlyBracketOpen = '{'
        private const val cCurlyBracketClose = '}'
        private const val cParenthesisOpen = '('
        private const val cParenthesisClose = ')'
        private const val cBracketOpen = '['
        private const val cBracketClose = ']'
        private const val cDoubleQuote = '"'
        private const val cComma = ','
        private const val cSemicolon = ';'

        private const val cSlash = '/'
        private const val cStar = '*'
        private const val cEscape = '\\'
        private const val cAnnotation = '@'
        private const val cColon = ':'

        private const val sLineComment = "//"
        private const val sBlockCommentStart = "/*"
        private const val sBlockCommentEnd = "*/"

        private val charBreaks = charArrayOf(
            cCurlyBracketOpen, cCurlyBracketClose, cParenthesisOpen,
            cParenthesisClose, cBracketOpen, cBracketClose, cDoubleQuote, cComma, cSemicolon
        )

        private const val sPackage = "package"
        private const val sImport = "import"
        private const val sClass = "class"
        private const val sAbstract = "abstract"
        private const val sInterface = "interface"
        private const val sExtends = "extends"
        private const val sImplements = "implements"
        private const val sPublic = "public"
        private const val sPrivate = "private"
        private const val sProtected = "protected"
        private const val sVoid = "void"
        private const val sStatic = "static"
        private const val sFinal = "final"
        private const val sNew = "new"
        private const val sSuper = "super"
        private const val sTry = "try"
        private const val sCatch = "catch"
        private const val sIf = "if"
        private const val sElse = "else"
        private const val sSwitch = "switch"
        private const val sCase = "case"
        private const val sBreak = "break"
        private const val sContinue = "continue"
        private const val sFor = "for"
        private const val sWhile = "while"
        private const val sReturn = "return"
        private const val sEnum = "enum"
        private const val sTransient = "transient"
        private const val sStrictfp = "strictfp"
        private const val sSynchronized = "synchronized"
        private const val sVolatile = "volatile"
        private const val sNative = "native"
        private const val sThrows = "throws"

        private val keywords = arrayOf(
            sPackage, sImport, sClass, sAbstract, sInterface, sExtends, sImplements, sPublic, sPrivate, sProtected,
            sVoid, sStatic, sFinal, sNew, sSuper, sTry, sCatch, sIf, sElse, sSwitch, sCase, sBreak, sContinue, sFor,
            sWhile, sReturn, sEnum, sTransient, sStrictfp, sSynchronized, sVolatile, sNative, sThrows
        )

        private const val tBool = "bool"
        private const val tBoolObject = "Boolean"
        private const val tByte = "byte"
        private const val tByteObject = "Byte"
        private const val tShort = "short"
        private const val tShortObject = "Short"
        private const val tInt = "int"
        private const val tIntObject = "Integer"
        private const val tDouble = "double"
        private const val tDoubleObject = "Double"
        private const val tLong = "long"
        private const val tLongObject = "Long"
        private const val tFloat = "float"
        private const val tFloatObject = "Float"
        private const val tChar = "char"
        private const val tCharObject = "Character"
        private const val tString = "String"
        private const val tStringBuffer = "StringBuffer"
        private const val tDate = "Date"
        private const val tArray = "Array"
        private const val tList = "List"
        private const val tArrayList = "ArrayList"
        private const val tMap = "Map"
        private const val tHashMap = "HashMap"
        private const val tHashTable = "HashTable"
        private const val tHashSet = "HashSet"
        private const val tStringBuilder = "StringBuilder"
        private const val tObject = "Object"
        private const val tNumber = "Number"
        private const val tEnum = "Enum"
        private const val tException = "Exception"

        private val types = arrayOf(
            tBool, tBoolObject, tByte, tByteObject, tShort, tShortObject, tInt, tIntObject, tDouble, tDoubleObject,
            tLong, tLongObject, tFloat, tFloatObject, tChar, tCharObject, tString, tStringBuffer, tDate, tArray,
            tList, tArrayList, tMap, tHashMap, tHashSet, tHashTable, tStringBuilder, tObject, tNumber, tEnum, tException
        )

        /**
         * Return True is the keyword is a Java instruction
         */
        private fun isInstruction(word: String): Boolean {
            for (k in keywords) {
                if (k.equals(word, ignoreCase = true)) return true
            }
            return false
        }

        private fun isBreakCharacter(word: String): Boolean {
            if (word.length == 1)
                return TextUtils.inCharactersList(charBreaks, word[0])
            return false
        }

        private fun parseAnnotation(block: CodeBlock) {
            var annotationName: String? = null
            val at = cAnnotation.toString()
            for (word in block.words) {
                if (word.value.startsWith(at)) {
                    annotationName = word.value.replace(at, "")
                    break
                }
            }
            block.name = annotationName
        }

        private fun parsePackageOrImportName(block: CodeBlock) {
            val sb = StringBuilder()
            for (word in block.words) {
                if (isBreakCharacter(word.value)) break
                if (word.value.trim().isNotEmpty() && !isBreakCharacter(word.value) && !isInstruction(word.value))
                    sb.append(word.value)
            }
            block.name = sb.toString()
        }
    }
}
