package com.volumebooster.pro.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShizukuUtils {
    fun isShizukuReady(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openShizukuSettings(context: Context) {
        val intent = Intent().apply {
            setPackage("moe.shizuku.privileged.api")
            action = "moe.shizuku.privileged.api.settings"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
