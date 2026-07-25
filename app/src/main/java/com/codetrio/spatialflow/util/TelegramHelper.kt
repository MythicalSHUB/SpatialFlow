package com.codetrio.spatialflow.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TelegramHelper {
    
    /**
     * Attempts to open the Telegram app either with a specific message or to a specific domain (channel/group).
     * Falls back to opening the standard web intent if the app is not installed or cannot handle the intent.
     * 
     * @param context The Context used to start the Activity and show toasts.
     * @param message If provided, will open a share intent with this text.
     * @param domain If provided (e.g. "SpatialFlow"), will open that specific channel/group.
     */
    fun openTelegram(context: Context, message: String? = null, domain: String? = null) {
        try {
            val tgUri = if (message != null) {
                Uri.parse("tg://msg?text=${Uri.encode(message)}")
            } else {
                Uri.parse("tg://resolve?domain=${domain ?: "SpatialFlow"}")
            }
            
            val intent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to https://t.me/
            val fallbackUri = if (message != null) {
                Uri.parse("https://t.me/share/url?url=&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://t.me/${domain ?: "SpatialFlow"}")
            }
            
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open browser for Telegram link", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
