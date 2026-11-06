package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object TelegramHelper {
    private const val TAG = "TelegramHelper"
    const val CHANNEL_URL = "https://t.me/dh_method"
    const val CHANNEL_USERNAME = "dh_method"
    const val SUPPORT_URL = "https://t.me/argentina1918"
    const val SUPPORT_USERNAME = "argentina1918"

    fun openTelegramUrl(context: Context, username: String, fullUrl: String) {
        try {
            // First attempt: Open directly in Telegram native app
            val tgUri = Uri.parse("tg://resolve?domain=$username")
            val tgIntent = Intent(Intent.ACTION_VIEW, tgUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(tgIntent)
        } catch (e: Exception) {
            Log.d(TAG, "Telegram app not found or could not handle uri: ${e.message}")
            try {
                // Fallback attempt: Open in standard web browser
                val webUri = Uri.parse(fullUrl)
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open link in browser: $fullUrl", t)
            }
        }
    }

    fun openChannel(context: Context) {
        openTelegramUrl(context, CHANNEL_USERNAME, CHANNEL_URL)
    }

    fun openSupport(context: Context) {
        openTelegramUrl(context, SUPPORT_USERNAME, SUPPORT_URL)
    }
}
