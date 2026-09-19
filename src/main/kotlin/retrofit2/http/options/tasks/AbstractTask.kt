package retrofit2.http.options.tasks

import retrofit2.http.options.utils.TextUtils
import retrofit2.http.options.utils.Utils
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Abstract Enigma Gradle Task
 */
@DisableCachingByDefault(because = "Tasks rewrite source files in place and depend on SCM state")
abstract class AbstractTask : DefaultTask() {

    @Internal
    var debug = false
    @Internal
    var rootProject: String? = null
    @Internal
    var pathSrc: String? = null
    @Internal
    var pathSrcKotlin: String? = null

    init {
        this.group = "enigma"
    }

    /**
     * Return the collection of all JAVA files (*.java) found in the app source folder
     */
    @Internal
    protected fun getAllJavaFiles(): Collection<File> {
        return Utils.listFileTree(pathSrc?.let(::File), ".java")
    }

    /**
     * Return the collection of all Kotlin files (*.kt) found in the app source folder
     */
    @Internal
    protected fun getAllKotlinFiles(): Collection<File> {
        val dir = if (!TextUtils.isEmpty(pathSrcKotlin)) pathSrcKotlin else pathSrc
        return Utils.listFileTree(dir?.let(::File), ".kt")
    }

    /**
     * Return all source files (*.java + *.kt), without duplicates
     */
    @Internal
    protected fun getAllSourceFiles(): Collection<File> {
        val files = HashSet(getAllJavaFiles())
        files.addAll(getAllKotlinFiles())
        return files
    }

    /**
     * Check if file is a Kotlin source file
     */
    protected fun isKotlinFile(file: File): Boolean = file.name.endsWith(".kt")

    /**
     * Return the collection of all XML files (*.xml) found in the app source folder
     */
    @Internal
    protected fun getAllXmlFiles(): Collection<File> {
        return Utils.listFileTree(pathSrc?.let(::File), ".xml")
    }

    /**
     * Check if an SCM (Source Code Management) tool is setup or not not.
     */
    protected fun checkSCM(): Boolean {
        val result = hasGit() || hasSubversion() || hasMercurial()
        if (!result) {
            println("⚠️ The project has no Source Code Management. Please setup one (Git, SVN, Mercurial) before use Enigma plugin!")
        }
        return result
    }

    private fun findScmFolder(folderName: String): Boolean {
        var current: File? = rootProject?.let(::File)
        while (current != null) {
            if (File(current, folderName).exists()) return true
            current = current.parentFile
        }
        return false
    }

    /**
     * Check if an Subversion tool is setup or not not.
     */
    private fun hasSubversion(): Boolean = findScmFolder(SVN_FOLDER)

    /**
     * Check if an Mercurial tool is setup or not not.
     */
    private fun hasMercurial(): Boolean = findScmFolder(MERCURIAL_FOLDER)

    /**
     * Check if an Git tool is setup or not not.
     */
    private fun hasGit(): Boolean = findScmFolder(GIT_FOLDER)

    /**
     * Return the file path of 'backup' directory of the current project
     */
    protected fun backupDir(): String {
        return rootProject + File.separator + BACKUP_DIR + File.separator
    }

    /**
     * Check if 'backup' directory exists or not
     */
    protected fun backupDirExists(): Boolean {
        return File(backupDir()).exists()
    }

    /**
     * Remove the existing 'backup' directory
     */
    protected fun removeBackupDir() {
        val dir = File(backupDir())
        if (dir.exists()) {
            try {
                FileUtils.deleteDirectory(dir)
            } catch (e: Exception) {
                println("⚠️ Warning: could not delete backup directory: ${e.message}")
            }
        }
    }

    /**
     * Create the 'backup' directory (with .gitignore config file)
     */
    protected fun createBackupDir(): Boolean {
        val backupDir = File(backupDir())
        val gitIgnore = File(backupDir().toString() + File.separator + ".gitignore")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        if (backupDir.exists() && !gitIgnore.exists()) {
            try {
                FileUtils.writeStringToFile(gitIgnore, GIT_IGNORE, "UTF-8")
            } catch (_: Exception) {}
        }
        return true
    }

    @Internal
    var maskPackage: String? = "kotlinx.coroutines.flow"
    @Internal
    var mode: String? = "masking"

    /**
     * Check if file is an injected Enigma decryptor file
     */
    protected fun isEnigmaFile(srcFile: File): Boolean {
        val name = srcFile.name
        val path = srcFile.absolutePath.replace(File.separator, "/")
        val maskPkgPath = (maskPackage ?: "kotlinx.coroutines.flow").replace(".", "/")
        return name == InjectCodeTask.CLASS_NAME + ".java" ||
            name == InjectCodeTask.CLASS_NAME + ".kt" ||
            (name == "String.kt" && path.contains(maskPkgPath))
    }

    /**
     * Check if the JAVA/Kotlin file contains Enigma code
     */
    protected fun isEnigmatized(srcFile: File): Boolean {
        val contents = FileUtils.readFileToString(srcFile, "UTF-8")
        // Kotlin imports have no trailing semicolon, so match both styles
        val kotlinImport = InjectCodeTask.IMPORT_NAME.replace(";", "")
        val maskPkg = maskPackage ?: "kotlinx.coroutines.flow"
        return contents.contains(InjectCodeTask.IMPORT_NAME) ||
            contents.contains(kotlinImport) ||
            contents.contains(InjectCodeTask.FUNCTION_NAME) ||
            contents.contains("import $maskPkg.lowercase") ||
            contents.contains("import $maskPkg.uppercase") ||
            contents.contains("import $maskPkg.trim")
    }

    companion object {
        private const val BACKUP_DIR = "enigma-backup"
        private const val SVN_FOLDER = ".svn"
        private const val MERCURIAL_FOLDER = ".hg"
        private const val GIT_FOLDER = ".git"
        private const val GIT_IGNORE = "*\n*/\n!.gitignore"
    }
}
