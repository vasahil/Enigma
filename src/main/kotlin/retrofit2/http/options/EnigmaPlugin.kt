package retrofit2.http.options

import retrofit2.http.options.tasks.BackupTask
import retrofit2.http.options.tasks.CleanBackupTask
import retrofit2.http.options.tasks.EnigmaTask
import retrofit2.http.options.tasks.InjectCodeTask
import retrofit2.http.options.tasks.RestoreTask
import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Enigma Gradle Plugin
 */
class EnigmaPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        // https://docs.gradle.org/current/userguide/custom_plugins.html
        val extension = project.extensions.create("enigma", EnigmaPluginExtension::class.java)

        // Generate random hash key, if not defined
        if (TextUtils.isEmpty(extension.hash))
            extension.hash = Utils.randomHashKey()

        project.afterEvaluate {

            // Search custom Encryption task:
            val customEncryptTask = extension.encryptionTaskName
                ?.let { taskName -> project.tasks.findByName(taskName) }
                ?.also { println("⚙️ Custom Encryption Task found: ${extension.encryptionTaskName}") }

            project.tasks.create("cleanBackup", CleanBackupTask::class.java) {
                it.enabled = extension.enabled
                it.rootProject = project.rootDir.absolutePath
                it.pathSrc = project.rootDir.absolutePath + extension.srcJava
                it.pathSrcKotlin = project.rootDir.absolutePath + extension.srcKotlin
                it.maskPackage = extension.maskPackage
                it.mode = extension.mode
                it.debug = extension.debug
            }

            project.tasks.create("backup", BackupTask::class.java) {
                it.enabled = extension.enabled
                it.rootProject = project.rootDir.absolutePath
                it.pathSrc = project.rootDir.absolutePath + extension.srcJava
                it.pathSrcKotlin = project.rootDir.absolutePath + extension.srcKotlin
                it.maskPackage = extension.maskPackage
                it.mode = extension.mode
                it.debug = extension.debug
            }

            project.tasks.create("injectCode", InjectCodeTask::class.java) {
                it.enabled = extension.enabled
                it.rootProject = project.rootDir.absolutePath
                it.pathSrc = project.rootDir.absolutePath + extension.srcJava
                it.pathSrcKotlin = project.rootDir.absolutePath + extension.srcKotlin
                it.hash = extension.hash
                it.customFunction = extension.customFunction
                it.mode = extension.mode
                it.maskKeywords = extension.maskKeywords
                it.maskPackage = extension.maskPackage
                it.salt = extension.salt
                it.debug = extension.debug
            }

            project.tasks.create("encrypt", EnigmaTask::class.java) {
                it.enabled = extension.enabled
                it.rootProject = project.rootDir.absolutePath
                it.pathSrc = project.rootDir.absolutePath + extension.srcJava
                it.pathSrcKotlin = project.rootDir.absolutePath + extension.srcKotlin
                it.hash = extension.hash
                it.ignoredClasses = extension.ignoredClasses
                it.classes = extension.classes
                it.customFunction = extension.customFunction
                it.customEncryptionTask = customEncryptTask as? DefaultTask
                it.injectFakeKeys = extension.injectFakeKeys
                it.mode = extension.mode
                it.maskKeywords = extension.maskKeywords
                it.maskPackage = extension.maskPackage
                it.maskWordsCount = extension.maskWordsCount
                it.customWords = extension.customWords
                it.salt = extension.salt
                it.debug = extension.debug
            }

            project.tasks.create("restore", RestoreTask::class.java) {
                it.enabled = extension.enabled
                it.rootProject = project.rootDir.absolutePath
                it.pathSrc = project.rootDir.absolutePath + extension.srcJava
                it.pathSrcKotlin = project.rootDir.absolutePath + extension.srcKotlin
                it.maskPackage = extension.maskPackage
                it.mode = extension.mode
                it.debug = extension.debug
            }

            project.tasks.findByName("clean")?.dependsOn("cleanBackup")

            project.tasks.findByName("backup")?.mustRunAfter("cleanBackup")
            project.tasks.findByName("injectCode")?.mustRunAfter("backup")
            project.tasks.findByName("encrypt")?.mustRunAfter("injectCode")

            project.tasks.findByName("preBuild")?.let { preBuild ->
                preBuild.dependsOn("backup")
                preBuild.dependsOn("injectCode")
                preBuild.dependsOn("encrypt")
            }

            val restoreTask = project.tasks.named("restore")

            if (project.plugins.hasPlugin("com.android.application") || project == project.rootProject) {
                project.tasks.configureEach { task ->
                    val name = task.name
                    if (name == "assembleRelease" || name == "assembleDebug" ||
                        name == "bundleRelease" || name == "bundleDebug") {
                        task.finalizedBy(restoreTask)
                    }
                }
            } else {
                // Library module: finalize restore ONLY when the application module or root project finishes assemble/bundle
                project.rootProject.tasks.configureEach { task ->
                    val name = task.name
                    if (name == "assembleRelease" || name == "assembleDebug" ||
                        name == "bundleRelease" || name == "bundleDebug") {
                        task.finalizedBy(restoreTask)
                    }
                }
                project.rootProject.subprojects { sub ->
                    sub.plugins.withId("com.android.application") {
                        sub.tasks.configureEach { task ->
                            val name = task.name
                            if (name == "assembleRelease" || name == "assembleDebug" ||
                                name == "bundleRelease" || name == "bundleDebug") {
                                task.finalizedBy(restoreTask)
                            }
                        }
                    }
                }
            }
        }
    }
}
