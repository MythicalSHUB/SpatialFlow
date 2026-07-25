package com.codetrio.spatialflow.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberSupportedHighestFps(): Float {
    val view = LocalView.current

    return remember(view) {
        val display = view.display
        display?.supportedModes
            ?.maxOfOrNull { mode -> mode.refreshRate }
            ?: display?.refreshRate
            ?: 60f // Fallback to 60 FPS
    }
}

private fun applyRefreshRate(
    view: View,
    activity: Activity?,
    requestedFps: Float,
) {
    // For Android 15 (Vanilla Ice Cream) and above
    if (Build.VERSION.SDK_INT >= 35) {
        view.setRequestedFrameRate(requestedFps)
        return
    }

    // For Android 14 and below
    activity?.window?.let { window ->
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != requestedFps) {
            attributes.preferredRefreshRate = requestedFps
            window.attributes = attributes
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun ApplyRefreshRate(
    isEnabled: Boolean,
    targetFps: Float,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    
    // 0f tells the system to use its default dynamic refresh rate handling
    val requestedFps = if (isEnabled) targetFps else 0f

    DisposableEffect(view, activity, requestedFps) {
        applyRefreshRate(
            view = view,
            activity = activity,
            requestedFps = requestedFps,
        )

        onDispose {
            // Restore default when leaving
            applyRefreshRate(
                view = view,
                activity = activity,
                requestedFps = 0f, 
            )
        }
    }
}
