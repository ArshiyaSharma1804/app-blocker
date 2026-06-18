package com.example.appblocker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages a persistent whitelist of APK package names stored in SharedPreferences.
 * The Accessibility Service checks this whitelist before blocking an installation.
 */
object WhitelistManager {

    private const val PREFS_NAME = "apk_whitelist_prefs"
    private const val KEY_WHITELIST = "whitelisted_entries"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Add an APK to the whitelist.
     * Stored as "packageName::appName::filePath" for display and matching purposes.
     */
    fun addToWhitelist(context: Context, packageName: String, appName: String, filePath: String) {
        val prefs = getPrefs(context)
        val entries = prefs.getStringSet(KEY_WHITELIST, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        entries.add("$packageName::$appName::$filePath")
        prefs.edit().putStringSet(KEY_WHITELIST, entries).apply()
    }

    /**
     * Remove an entry from the whitelist by its full key.
     */
    fun removeFromWhitelist(context: Context, entryKey: String) {
        val prefs = getPrefs(context)
        val entries = prefs.getStringSet(KEY_WHITELIST, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        entries.remove(entryKey)
        prefs.edit().putStringSet(KEY_WHITELIST, entries).apply()
    }

    /**
     * Check if a package name is whitelisted.
     */
    fun isPackageWhitelisted(context: Context, packageName: String): Boolean {
        val entries = getPrefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        return entries.any { it.startsWith("$packageName::") }
    }

    /**
     * Get all whitelisted app names (used by the Accessibility Service to match
     * against the text shown on the Package Installer screen).
     */
    fun getWhitelistedAppNames(context: Context): Set<String> {
        val entries = getPrefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        return entries.mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 2) parts[1] else null
        }.toSet()
    }

    /**
     * Get all whitelisted app names AND package names as match terms.
     * Used by the Accessibility Service to match against ALL text on the installer screen.
     */
    fun getWhitelistedMatchTerms(context: Context): Set<String> {
        val entries = getPrefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        val terms = mutableSetOf<String>()
        for (entry in entries) {
            val parts = entry.split("::")
            if (parts.size >= 2) {
                terms.add(parts[0]) // package name
                terms.add(parts[1]) // app name
            }
        }
        return terms
    }

    /**
     * Get all raw whitelist entries for the WTL screen.
     */
    fun getAllEntries(context: Context): List<WhitelistEntry> {
        val entries = getPrefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        return entries.mapNotNull { entry ->
            val parts = entry.split("::")
            if (parts.size >= 3) {
                WhitelistEntry(
                    packageName = parts[0],
                    appName = parts[1],
                    filePath = parts[2],
                    rawKey = entry
                )
            } else null
        }.sortedBy { it.appName }
    }
}

data class WhitelistEntry(
    val packageName: String,
    val appName: String,
    val filePath: String,
    val rawKey: String
)
