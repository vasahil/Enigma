package retrofit2.http.options.utils

/**
 * Smartest Array List
 */
class SmartArrayList<T> : ArrayList<T>() {

    /**
     * Return the first element
     */
    fun first(): T = this[0]

    /**
     * Return the last element
     */
    fun last(): T = this[size - 1]

    /**
     * Return the previous element, or null if index is 0
     */
    fun previous(i: Int): T? = if (i > 0) this[i - 1] else null

    /**
     * Return the next element if exits otherwise null
     */
    fun next(i: Int): T? = if (size > i + 1) this[i + 1] else null

    /**
     * Return the value of the last index, or -1 if array is empty
     */
    fun lastIndex(): Int = size - 1
}
