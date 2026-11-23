package io.reader.android.runtime

import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Process
import android.system.Os
import android.system.OsConstants
import io.reader.android.ReaderApplication
import java.io.File

/** Production-only closed-world prerequisites. This is not an arbitrary UUID death proof. */
internal class ReaderProgressOriginBoundary(private val application: ReaderApplication,
    private val actualOwner: AppCoreOwner, private val root: File, private val coreDirectory: String) {
    // Package metadata cannot change in this Application incarnation. The raw files, live client,
    // protocol state and exact bytes are deliberately NOT cached by this boundary.
    private var packageVerified = false
    private var packageDataRoot: File? = null
    @Suppress("DEPRECATION")
    fun requireSupported() {
        check(application.applicationContext === application && application.coreOwner === actualOwner) { "PROGRESS_INTENT_NOT_APP_SINGLETON" }
        check(application.applicationInfo.uid == Process.myUid()) { "PROGRESS_INTENT_FOREIGN_USER_ROOT" }
        if (!packageVerified) verifyPackage()
        check(packageDataRoot == application.filesDir.parentFile?.canonicalFile) { "PROGRESS_INTENT_FOREIGN_USER_ROOT" }
        requireRealDirectory(checkNotNull(packageDataRoot)); requireRealDirectory(application.filesDir)
        val expected = File(application.filesDir, "Reader")
        requireRealDirectory(expected)
        check(expected.canonicalFile == root && File(root, "Core").canonicalPath == coreDirectory) { "PROGRESS_INTENT_FOREIGN_CORE_ROOT" }
        requireRealDirectory(File(root, "Core")); requireRealDirectory(File(root, "Operations"))
    }
    @Suppress("DEPRECATION")
    private fun verifyPackage() {
        val pkg = application.packageName
        val info = application.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
        val app = checkNotNull(info.applicationInfo)
        check(app.packageName == pkg && app.uid == Process.myUid() && application.applicationInfo.uid == Process.myUid() &&
            app.processName == pkg && info.sharedUserId == null && (app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0) { "PROGRESS_INTENT_UNSUPPORTED_PROCESS_BOUNDARY" }
        fun component(value: ComponentInfo) = value.packageName == pkg && value.processName == pkg
        check(info.activities.orEmpty().all { component(it) } && info.services.orEmpty().all { component(it) } &&
            info.receivers.orEmpty().all { component(it) } && info.providers.orEmpty().all { component(it) }) { "PROGRESS_INTENT_MULTIPROCESS_BOUNDARY" }
        packageDataRoot = File(app.dataDir).canonicalFile
        packageVerified = true
    }
    companion object {
        fun requireRealDirectory(file: File) {
            val stat = Os.lstat(file.absolutePath)
            check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == Process.myUid()) { "PROGRESS_INTENT_SYMLINK_OR_FOREIGN_DIRECTORY" }
        }
        fun requireRegular(file: File) {
            val stat = Os.lstat(file.absolutePath)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == Process.myUid()) { "PROGRESS_INTENT_SYMLINK_OR_FOREIGN_FILE" }
        }
    }
}
