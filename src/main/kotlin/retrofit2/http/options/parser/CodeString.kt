package retrofit2.http.options.parser

/**
 * Represent a keyword a JAVA code
 */
class CodeString(var start: Int) {

    /**
     * Word value
     */
    var value: String = ""

    /**
     * Word end position
     */
    var end: Int = 0

    /**
     * Indicate if word is a JAVA keyword (instruction)
     */
    var isInstruction = false

    /**
     * Indicate if word is a JAVA native Type (int, string...)
     */
    var isType = false

    /**
     * Indicate if the word is a value of Switch/Case conditional block
     */
    var isCaseValue = false

    override fun toString(): String = "[$start, $end] '$value'"
}
