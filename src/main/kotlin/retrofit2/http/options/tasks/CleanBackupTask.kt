package retrofit2.http.options.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * Gradle Task to clean Backup directory.
 */
@DisableCachingByDefault(because = "Deletes the backup directory")
open class CleanBackupTask @Inject constructor() : AbstractTask() {

    @TaskAction
    fun cleanBackup() {
        if (!enabled) return
        if (!checkSCM()) return
        this.removeBackupDir()
        println("\uD83E\uDDF9 Backup clean: " + backupDir())
    }
}
