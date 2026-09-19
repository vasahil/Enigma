package retrofit2.http.options.tasks

import retrofit2.http.options.utils.MaskUtils
import retrofit2.http.options.utils.TextUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Gradle Task to restore original JAVA files after compilation.
 */
@DisableCachingByDefault(because = "Restores sources from backup in place")
open class RestoreTask @Inject constructor() : AbstractTask() {

    @TaskAction
    fun restore() {
        if (!enabled) return
        if (!checkSCM()) return

        if (backupDirExists()) {
            for (srcFile in this.getAllSourceFiles()) {
                this.restoreFile(srcFile)
            }
        } else {
            println("⚠️ There is no backup to restore!")
        }
        removeEnigmaCode()
    }

    private fun removeEnigmaCode() {
        val codePackage = File(pathSrc + File.separator + InjectCodeTask.PACKAGE_NAME.replace(".", File.separator))
        if (codePackage.exists()) {
            FileUtils.deleteDirectory(codePackage)
            println("🧹 Remove Enigma code: " + codePackage.absolutePath)
        }
        // Kotlin sources may live in a different root: clean the decryptor there too
        if (pathSrcKotlin != null && pathSrcKotlin != pathSrc) {
            val kotlinPackage = File(pathSrcKotlin + File.separator + InjectCodeTask.PACKAGE_NAME.replace(".", File.separator))
            if (kotlinPackage.exists()) {
                FileUtils.deleteDirectory(kotlinPackage)
                println("🧹 Remove Enigma Kotlin code: " + kotlinPackage.absolutePath)
            }
        }

        // Clean MaskWeb injected String.kt
        val pkg = maskPackage ?: MaskUtils.DEFAULT_PACKAGE
        val kotlinSrc = if (!TextUtils.isEmpty(pathSrcKotlin)) pathSrcKotlin else pathSrc
        val maskFile = File(kotlinSrc + File.separator + pkg.replace(".", File.separator) + File.separator + "String.kt")
        if (maskFile.exists()) {
            maskFile.delete()
            println("🧹 Remove MaskWeb String.kt: " + maskFile.absolutePath)
            var parentDir: File? = maskFile.parentFile
            val rootSrc = File(kotlinSrc)
            while (parentDir != null && parentDir != rootSrc && parentDir.listFiles()?.isEmpty() == true) {
                parentDir.delete()
                parentDir = parentDir.parentFile
            }
        }
    }

    private fun restoreFile(file: File) {
        val srcFile = file.absolutePath.replace(rootProject.toString(), "")
        if (isEnigmaFile(file)) return

        val backup = File(backupDir() + srcFile)
        if (backup.exists() && backup.length() > 0) {
            FileUtils.copyFile(backup, file)
            println("♻️ Restore: $srcFile")
        }
    }
}
