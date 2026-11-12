package com.aurora.iptv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.iptv.AuroraApp
import com.aurora.iptv.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android TV boxes restrict background activity launches on newer versions, but a
 * BOOT_COMPLETED receiver still works on most TV launchers when the user enabled
 * autostart. Failures are silent because there is no UI to report them at boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val application = context.applicationContext as? AuroraApp ?: return
        val pending = goAsync()
        application.container.applicationScope.launch {
            try {
                val enabled = application.container.settings.snapshot().autostart
                if (!enabled) return@launch
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_ROUTE, "live")
                }
                runCatching { context.startActivity(launch) }
            } finally {
                pending.finish()
            }
        }
    }
}
