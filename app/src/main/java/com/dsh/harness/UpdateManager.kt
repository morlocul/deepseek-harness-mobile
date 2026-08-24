package com.dsh.harness

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
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

    /** Downloads the APK. Returns the byte size on success, -1 on failure. */
    suspend fun download(url: String, file: File): Long = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            var written = 0L
            OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext -1L
                val body = resp.body ?: return@withContext -1L
                body.byteStream().use { ins -> file.outputStream().use { outs -> written = ins.copyTo(outs) } }
            }
            written
        } catch (_: Exception) { -1L }
    }

    /** Hands the downloaded APK to the system package installer (reliable, shows the standard dialog). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
