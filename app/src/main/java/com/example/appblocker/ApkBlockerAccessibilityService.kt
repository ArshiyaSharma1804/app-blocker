package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.appblocker.data.InstallLogManager
import com.example.appblocker.data.WhitelistManager

class ApkBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Check if the event is from a package installer
        if (packageName == "com.google.android.packageinstaller" ||
            packageName == "com.android.packageinstaller" ||
            packageName == "com.miui.packageinstaller" ||
            packageName == "com.oplus.packageinstaller" ||
            packageName == "com.samsung.android.packageinstaller"
        ) {
            Log.d("ApkBlocker", "Package installer detected: $packageName")

            // --- OS-LEVEL INSTALLATION CHECK (Physical Code API) ---
            // Modern Android installations (like from Chrome) create a PackageInstaller.Session.
            // We can read all active install sessions directly from the OS to get the EXACT package name being installed!
            val pi = packageManager.packageInstaller
            val activeSessions = pi.allSessions
            val activeInstallPackages = activeSessions.mapNotNull { it.appPackageName }
            
            // Get our whitelisted package names
            val whitelistedPackageNames = WhitelistManager.getWhitelistedMatchTerms(this)
                .filter { it.contains(".") } // package names usually contain dots
            
            val sessionMatch = activeInstallPackages.firstOrNull { activePkg ->
                whitelistedPackageNames.any { it.equals(activePkg, ignoreCase = true) }
            }

            if (sessionMatch != null) {
                Log.d("ApkBlocker", "OS Session match found for '$sessionMatch'. Allowing install.")
                return
            }

            // --- UNINSTALL CHECK ---
            // If the user is trying to UNINSTALL an app, we should NOT block it.
            // Uninstalls also use the package installer.
            val rootNode = rootInActiveWindow
            val allScreenText = mutableListOf<String>()
            if (rootNode != null) {
                collectAllText(rootNode, allScreenText)
            }
            event.text?.forEach { t -> if (t != null) allScreenText.add(t.toString()) }

            val combinedText = allScreenText.joinToString(" ")
            
            // Check for common uninstall prompts
            if (combinedText.contains("uninstall", ignoreCase = true) || 
                combinedText.contains("do you want to uninstall", ignoreCase = true)) {
                Log.d("ApkBlocker", "Uninstall detected. Allowing.")
                return
            }

            // --- FALLBACK SCREEN TEXT CHECK ---
            // For older file managers that use ACTION_VIEW, a session might not exist until the user clicks Install.
            // We fall back to checking the screen text as a secondary measure.
            
            val matchTerms = WhitelistManager.getWhitelistedMatchTerms(this)
            val matchedTerm = matchTerms.firstOrNull { term ->
                term.isNotBlank() && combinedText.contains(term, ignoreCase = true)
            }

            if (matchedTerm != null) {
                Log.d("ApkBlocker", "Fallback UI match found: '$matchedTerm'. Allowing install.")
                return
            }

            // Not whitelisted — extract a label for logging purposes
            val appLabel = extractAppLabel(allScreenText)
            if (appLabel.isNotBlank()) {
                Log.d("ApkBlocker", "Blocked install of '$appLabel'")
                InstallLogManager.logBlockedAttempt(this, appLabel)
            } else {
                Log.d("ApkBlocker", "Blocked install (could not detect app name)")
            }

            // Block the installation
            if (rootNode != null) {
                val cancelNodes = rootNode.findAccessibilityNodeInfosByText("Cancel")
                for (node in cancelNodes) {
                    if (node.isClickable) {
                        Log.d("ApkBlocker", "Clicking Cancel button")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
                Log.d("ApkBlocker", "Performing Global Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                Log.d("ApkBlocker", "Root node is null, Performing Global Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    /**
     * Extract a human-readable label from the installer screen text for logging.
     */
    private fun extractAppLabel(allText: List<String>): String {
        for (text in allText) {
            val patterns = listOf(
                Regex("want to install (.+?)\\?", RegexOption.IGNORE_CASE),
                Regex("install (.+?)\\?", RegexOption.IGNORE_CASE),
                Regex("installing (.+)", RegexOption.IGNORE_CASE),
            )
            for (regex in patterns) {
                val match = regex.find(text)
                if (match != null && match.groupValues.size > 1) {
                    return match.groupValues[1].trim()
                }
            }
        }
        // Fallback: first short text that isn't a button label
        for (text in allText) {
            val trimmed = text.trim()
            if (trimmed.isNotBlank() && trimmed.length in 2..60 &&
                !trimmed.equals("Cancel", ignoreCase = true) &&
                !trimmed.equals("Install", ignoreCase = true) &&
                !trimmed.equals("Settings", ignoreCase = true) &&
                !trimmed.equals("OK", ignoreCase = true)) {
                return trimmed
            }
        }
        return ""
    }

    private fun collectAllText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { texts.add(it.toString()) }
        node.contentDescription?.let { texts.add(it.toString()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllText(child, texts)
        }
    }

    override fun onInterrupt() {
        Log.d("ApkBlocker", "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ApkBlocker", "Accessibility Service Connected")
    }
}
