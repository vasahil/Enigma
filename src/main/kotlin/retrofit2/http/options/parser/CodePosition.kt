package retrofit2.http.options.parser

/**
 * This class represent the position (indexes) of the block into his parent block
 */
class CodePosition(var start: Int = -1, var end: Int = -1) {

    /**
     * Indicate if the position is valid or not
     */
    fun isValid(): Boolean {
        if (start == -1 || end == -1) return false
        return start < end
    }

    override fun toString(): String = if (isValid()) "[$start, $end]" else super.toString()
}
