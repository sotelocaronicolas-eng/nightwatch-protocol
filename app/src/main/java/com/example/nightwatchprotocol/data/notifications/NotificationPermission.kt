package com.example.nightwatchprotocol.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

interface NotificationPermission {
    /** True on Android 13+, where the user must grant POST_NOTIFICATIONS at run time. */
    val requiresRuntimePermission: Boolean

    /** Whether notifications can be shown right now (permission granted and not blocked in system settings). */
    fun isGranted(): Boolean
}

class AndroidNotificationPermission(private val context: Context) : NotificationPermission {

    override val requiresRuntimePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun isGranted(): Boolean {
        val permitted = !requiresRuntimePermission ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
