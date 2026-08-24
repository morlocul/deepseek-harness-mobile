package com.dsh.harness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Receives PackageInstaller session result; relaunches the app on success. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)
        when (code) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {}
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
                try {
                    context.startActivity(
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
            else -> {
                try { Toast.makeText(context, "Actualizare eșuată (cod $code)", Toast.LENGTH_LONG).show() } catch (_: Exception) {}
            }
        }
    }
}
