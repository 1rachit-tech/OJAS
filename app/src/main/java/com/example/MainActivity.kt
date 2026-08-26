package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.util.OjDeepLinkUtil
import com.example.ui.OjasApp

class MainActivity : ComponentActivity() {

    private var activeDeepLinkOjId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase App Check & Play Integrity Security
        com.example.data.security.OjasSecurityManager.initializeSecurity(this)

        // Extract deep link from cold start intent
        activeDeepLinkOjId = OjDeepLinkUtil.parseOjIdFromUri(intent?.data)

        setContent {
            OjasApp(
                initialDeepLinkOjId = activeDeepLinkOjId,
                onDeepLinkConsumed = {
                    activeDeepLinkOjId = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Extract deep link when app is already open in background
        val extractedId = OjDeepLinkUtil.parseOjIdFromUri(intent.data)
        if (extractedId != null) {
            activeDeepLinkOjId = extractedId
        }
    }
}


