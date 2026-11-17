package com.mvp93.adsutil.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.widget.Toast

/**
 * Unwraps a [Context] (e.g., [ContextWrapper]) to find the underlying [Activity], if any.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
