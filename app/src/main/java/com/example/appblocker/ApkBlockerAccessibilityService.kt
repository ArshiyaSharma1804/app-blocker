package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ApkBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        
        // Check if the event is from a package installer
        if (packageName == "com.google.android.packageinstaller" || packageName == "com.android.packageinstaller") {
            Log.d("ApkBlocker", "Package installer detected!")
            
            // In a real scenario, we might want to ensure it's actually an install screen
            // But for this requirement, we block all unknown source installs. Since we can't
            // know the source of the APK being installed easily from accessibility,
            // the requirement "blocks any .apk file to be installed if its source is not google playstore"
            // translates to blocking the Package Installer itself, as Play Store installs
            // apps silently in the background without showing the Package Installer UI.
            // If the Package Installer UI is showing, it's sideloading.
            
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                // Method 1: Try to find and click the "Cancel" button
                val cancelNodes = rootNode.findAccessibilityNodeInfosByText("Cancel")
                for (node in cancelNodes) {
                    if (node.isClickable) {
                        Log.d("ApkBlocker", "Clicking Cancel button")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
                
                // Method 2: Global back action if Cancel is not found
                Log.d("ApkBlocker", "Performing Global Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                Log.d("ApkBlocker", "Root node is null, Performing Global Back")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
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
