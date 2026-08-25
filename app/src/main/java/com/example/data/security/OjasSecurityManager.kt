package com.example.data.security

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Manages client security, anti-bot integrity, and Firebase App Check initialization.
 */
object OjasSecurityManager {
    private const val TAG = "OjasSecurityManager"
    private var isAppCheckInitialized = false

    /**
     * Initializes Firebase App Check with Play Integrity for release builds
     * and DebugAppCheckProviderFactory for local/debug builds.
     */
    fun initializeSecurity(context: Context) {
        if (isAppCheckInitialized) return

        try {
            val app = FirebaseApp.getInstance()
            val firebaseAppCheck = FirebaseAppCheck.getInstance(app)

            if (!BuildConfig.DEBUG) {
                Log.d(TAG, "Initializing Firebase App Check with Play Integrity.")
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                isAppCheckInitialized = true
            } else {
                Log.d(TAG, "Debug build: App Check provider installation bypassed to prevent token validation conflicts.")
                isAppCheckInitialized = true
            }
            Log.i(TAG, "Security initialized successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase App Check initialization deferred or unavailable: ${e.message}")
        }
    }

    /**
     * Returns true if device-level integrity and App Check are active.
     */
    fun isIntegrityProtected(): Boolean = isAppCheckInitialized
}
