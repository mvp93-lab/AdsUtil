package com.mvp93.adsutil.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast

/**
 * Checks if the application is currently running in debug mode.
 */
fun Context.isDebug(): Boolean {
    return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

/**
 * Displays a Toast message only when the app is running in debug mode.
 * In release/production builds, this is a no-op to prevent technical toasts
 * from appearing to end users.
 */
fun Context.showDebugToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    if (isDebug()) {
        Toast.makeText(this, message, duration).show()
    }
}
