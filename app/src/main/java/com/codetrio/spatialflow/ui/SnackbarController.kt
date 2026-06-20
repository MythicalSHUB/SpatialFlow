package com.codetrio.spatialflow.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SnackbarEvent(
    val message: String,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val iconResId: Int? = null,
    val iconVector: ImageVector? = null
)

class CustomSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    val iconResId: Int? = null,
    val iconVector: ImageVector? = null
) : SnackbarVisuals

object SnackbarController {
    private val _events = Channel<SnackbarEvent>(Channel.CONFLATED)
    val events = _events.receiveAsFlow()

    fun sendEvent(event: SnackbarEvent) {
        _events.trySend(event)
    }

    @kotlin.jvm.JvmOverloads
    fun showMessage(
        message: String,
        iconResId: Int? = null,
        iconVector: ImageVector? = null
    ) {
        sendEvent(
            SnackbarEvent(
                message = message,
                duration = SnackbarDuration.Short,
                iconResId = iconResId,
                iconVector = iconVector
            )
        )
    }
}
