package com.example.appblocker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks how many times an APK from unknown sources was blocked from installing.
 * Stored in SharedPreferences, keyed by the app name detected on the installer screen.
 */
object InstallLogManager {

    private const val PREFS_NAME = "install_log_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Increment the blocked attempt counter for the given app name.
     */
    fun logBlockedAttempt(context: Context, appName: String) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(appName, 0)
        prefs.edit().putInt(appName, currentCount + 1).apply()
    }

    /**
     * Get the number of times this app name was blocked.
     */
    fun getBlockedCount(context: Context, appName: String): Int {
        return getPrefs(context).getInt(appName, 0)
    }

    /**
     * Get all logged app names and their blocked attempt counts.
     */
    fun getAllLogs(context: Context): Map<String, Int> {
        val prefs = getPrefs(context)
        val allEntries = prefs.all
        val result = mutableMapOf<String, Int>()
        for ((key, value) in allEntries) {
            if (value is Int) {
                result[key] = value
            }
        }
        return result
    }
}
