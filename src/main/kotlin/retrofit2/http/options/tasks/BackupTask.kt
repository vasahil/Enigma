package retrofit2.http.options.tasks

import org.apache.commons.io.FileUtils
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * Gradle Task to backup original source code, before Enigma code injection.
 */
@DisableCachingByDefault(because = "Copies sources to a backup directory")
open class BackupTask @Inject constructor() : AbstractTask() {

    @TaskAction
    fun backup() {
        if (!enabled) return
        if (!checkSCM()) return
        this.createBackupDir()
        for (srcFile in this.getAllSourceFiles()) {
            this.backupFile(srcFile)
        }
    }

    /**
     * Backup a file into the backup directory
     */
    private fun backupFile(file: File) {
        val srcFile = file.absolutePath.replace(rootProject.toString(), "")
        if (isEnigmaFile(file)) return

        if (!isEnigmatized(file) && file.length() > 0) {
            val backup = File(backupDir() + srcFile)
            FileUtils.copyFile(file, backup)
            println("\uD83D\uDCBE Backup: $srcFile")
        } else {
            println("⚠️ Cannot backup an encrypted file: $srcFile")
        }
    }
}
