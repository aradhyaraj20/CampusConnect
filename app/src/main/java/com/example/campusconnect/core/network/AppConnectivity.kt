package com.example.campusconnect.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.campusconnect.core.security.SessionManager

object AppConnectivity {

    fun isOffline(context: Context): Boolean {
        if (SessionManager.getInstance(context).isOfflineModeEnabled()) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return true
                val caps = cm.getNetworkCapabilities(network) ?: return true
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected != true
            }
        } catch (_: Exception) {
            false
        }
    }
}
