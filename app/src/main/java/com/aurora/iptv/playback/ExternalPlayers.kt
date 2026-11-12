package com.aurora.iptv.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class ExternalPlayer(
    val packageName: String,
    val label: String,
    val known: Boolean
)

object ExternalPlayers {
    private val knownPlayers = linkedMapOf(
        "org.videolan.vlc" to "VLC",
        "com.mxtech.videoplayer.ad" to "MX Player",
        "com.mxtech.videoplayer.pro" to "MX Player Pro"
    )

    fun installed(context: Context): List<ExternalPlayer> =
        knownPlayers.mapNotNull { (packageName, label) ->
            if (isInstalled(context, packageName)) {
                ExternalPlayer(packageName, label, true)
            } else {
                null
            }
        }

    fun allTargets(context: Context): List<ExternalPlayer> {
        val result = installed(context).toMutableList()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/*")
        val handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        handlers.forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            if (pkg != context.packageName && result.none { it.packageName == pkg }) {
                result += ExternalPlayer(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    known = false
                )
            }
        }
        return result
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    fun launch(context: Context, url: String, packageName: String?): Boolean {
        val base = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(url), "video/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!packageName.isNullOrBlank()) base.setPackage(packageName)
        return try {
            context.startActivity(base)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(url), "*/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!packageName.isNullOrBlank()) fallback.setPackage(packageName)
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
