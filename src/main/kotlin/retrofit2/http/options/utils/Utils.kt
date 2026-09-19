package retrofit2.http.options.utils

import java.io.File
import java.util.Random

/**
 * Various helper functions
 */
object Utils {

    /**
     * Recursive algorithm to list all files in directory
     */
    fun listFileTree(dir: File?, fileType: String): Collection<File> {
        val fileTree = HashSet<File>()
        val files = dir?.listFiles() ?: return fileTree
        for (entry in files) {
            if (entry.isFile && entry.name.endsWith(fileType)) {
                fileTree.add(entry)
            } else {
                fileTree.addAll(listFileTree(entry, fileType))
            }
        }
        return fileTree
    }

    /**
     * Return the file path of a resource
     */
    fun getFileResource(fileName: String): File? {
        return try {
            val url = Utils::class.java.classLoader?.getResource(fileName) ?: return null
            val file = File(url.toURI())
            if (!file.exists()) null else file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate a random number between a range (included min and max values)
     */
    fun getRandomNumberInRange(min: Int, max: Int): Int {
        if (min >= max) {
            throw IllegalArgumentException("max must be greater than min")
        }
        val r = Random()
        return r.nextInt(max - min + 1) + min
    }

    /**
     * Insert an Object into an ArrayList at an specific index (without erase the existing one)
     */
    fun <T> insertInArray(arrayList: ArrayList<T>, index: Int, value: T) {
        if (index in 0..arrayList.size) {
            arrayList.add(index, value)
        } else {
            arrayList.add(value)
        }
    }

    /**
     * Convert a simple array to an ArrayList
     */
    fun toArrayList(array: Array<out Any?>): ArrayList<Any?> {
        val list = ArrayList<Any?>()
        for (o in array) {
            list.add(o)
        }
        return list
    }

    /**
     * Generate random hash key (32 characters)
     */
    fun randomHashKey(): String = TextUtils.getRandomString(32, TextUtils.KEY_CHARACTERS)

    /**
     * Check if an item exists in a array
     */
    fun arrayContains(array: Array<out Any?>, item: Any?): Boolean = findIndexInArray(array, item) != -1

    /**
     * Return the index of the element if it contains by the array, otherwise return -1
     */
    fun findIndexInArray(array: Array<out Any?>, item: Any?): Int {
        for (i in array.indices) {
            if (item == array[i]) return i
        }
        return -1
    }

    /**
     * Check if an array if not empty (and not null).
     */
    fun arrayNotEmpty(arrayList: ArrayList<*>?): Boolean = arrayList != null && arrayList.size > 0
}
