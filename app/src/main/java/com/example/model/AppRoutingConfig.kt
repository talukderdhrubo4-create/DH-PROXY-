package com.example.model

import android.graphics.drawable.Drawable

enum class RoutingMode(val title: String, val description: String) {
    ALL_APPS("All Apps", "Route all phone traffic through proxy"),
    SELECTED_ONLY("Only Selected Apps", "Only selected apps will use the proxy"),
    BYPASS_SELECTED("Bypass Selected Apps", "All apps use proxy EXCEPT selected ones")
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = false
)
