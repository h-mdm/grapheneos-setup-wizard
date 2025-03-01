package app.grapheneos.setupwizard.action

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileInputStream

object AppInstaller {
    private const val TAG = "AppInstaller"

    const val ACTION_INSTALL_COMPLETE = "INSTALL_COMPLETE"
    const val PACKAGE_NAME = "PACKAGE_NAME"

    fun silentInstallApplication(
        context: Context,
        file: File
    ) : String? {
        try {
            val packageManager: PackageManager = context.packageManager
            val packageInfo: PackageInfo = packageManager.getPackageArchiveInfo(file.path, 0)
                ?: throw Exception("Failed to parse the admin app package")

            val packageName = packageInfo.packageName

            Log.i(TAG, "Installing $packageName")
            val `in` = FileInputStream(file)
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(packageName)
            // set params
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            val out = session.openWrite("COSU", 0, -1)
            val buffer = ByteArray(65536)
            var c: Int
            while (`in`.read(buffer).also { c = it } != -1) {
                out.write(buffer, 0, c)
            }
            session.fsync(out)
            `in`.close()
            out.close()
            session.commit(
                createIntentSender(
                    context,
                    sessionId,
                    packageName
                )
            )
            Log.i(TAG, "Installation session committed")
            return null
        } catch (e: java.lang.Exception) {
            Log.w(TAG, "PackageInstaller error: " + e.message)
            e.printStackTrace()
            return e.message
        }
    }


    private fun createIntentSender(context: Context?, sessionId: Int, packageName: String?): IntentSender {
        val intent: Intent = Intent(ACTION_INSTALL_COMPLETE)
        if (packageName != null) {
            intent.putExtra(PACKAGE_NAME, packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        )
        return pendingIntent.intentSender
    }

    fun getPackageInstallerStatusMessage(status: Int): String {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> return "PENDING_USER_ACTION"
            PackageInstaller.STATUS_SUCCESS -> return "SUCCESS"
            PackageInstaller.STATUS_FAILURE -> return "FAILURE_UNKNOWN"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> return "BLOCKED"
            PackageInstaller.STATUS_FAILURE_ABORTED -> return "ABORTED"
            PackageInstaller.STATUS_FAILURE_INVALID -> return "INVALID"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> return "CONFLICT"
            PackageInstaller.STATUS_FAILURE_STORAGE -> return "STORAGE"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> return "INCOMPATIBLE"
        }
        return "UNKNOWN"
    }
}