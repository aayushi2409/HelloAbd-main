package com.example.hello_abd.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppUsageService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""
            val className = event.className?.toString() ?: ""
            
            // Check if it's a regular phone dialer call
            val isPhoneCall = packageName.contains("dialer", ignoreCase = true) || 
                             packageName.contains("telecom", ignoreCase = true) ||
                             packageName.contains("incallui", ignoreCase = true)

            // Specifically detect WhatsApp Voice/Video Call screen
            // WhatsApp uses 'VoipActivityV2' for its calls
            val isWhatsAppCall = packageName == "com.whatsapp" && className.contains("VoipActivity", ignoreCase = true)

            val isStealthAppActive = isPhoneCall || isWhatsAppCall

            val intent = Intent("com.example.hello_abd.STEALTH_APP_STATUS")
            intent.putExtra("IS_STEALTH_APP_ACTIVE", isStealthAppActive)
            sendBroadcast(intent)
            
            Log.d("AppUsageService", "App: $packageName | Class: $className | Stealth Active: $isStealthAppActive")
        }
    }

    override fun onInterrupt() {}
}
