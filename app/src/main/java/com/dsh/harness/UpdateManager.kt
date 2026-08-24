package com.dsh.harness

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object UpdateManager {

    fun installedCode(context: Context): Int =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionCode } catch (_: Exception) { 0 }

    fun needsInstallPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls().not()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun download(url: String, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                body.byteStream().use { ins -> file.outputStream().use { outs -> ins.copyTo(outs) } }
            }
            true
        } catch (_: Exception) { false }
    }

    fun install(context: Context, apk: File): Boolean = try {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val session = installer.openSession(installer.createSession(params))
        val out = session.openWrite("update", 0, apk.length())
        apk.inputStream().use { it.copyTo(out) }
        session.fsync(out)
        out.close()
        val pi = PendingIntent.getBroadcast(
            context, 1001, Intent(context, UpdateReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.commit(pi.intentSender)
        session.close()
        true
    } catch (_: Exception) { false }
}
