package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Utility for OJ content sharing and deep link resolution.
 * Enforces clean link generation and safe URI parsing without exposing private storage or credentials.
 */
object OjDeepLinkUtil {

    private const val WEB_DOMAIN = "ojas.app"
    private const val SCHEME_CUSTOM = "ojas"
    private const val HOST_OJ = "oj"

    /**
     * Builds a public web link representation for an OJ video.
     */
    fun generatePublicOjLink(ojId: String): String {
        val sanitizedId = ojId.trim()
        return "https://$WEB_DOMAIN/oj/$sanitizedId"
    }

    /**
     * Builds a custom scheme deep link for direct app routing.
     */
    fun generateCustomSchemeOjUri(ojId: String): String {
        val sanitizedId = ojId.trim()
        return "$SCHEME_CUSTOM://$HOST_OJ/$sanitizedId"
    }

    /**
     * Builds clean, user-friendly share text containing caption and link.
     */
    fun buildShareText(caption: String, ojId: String): String {
        val link = generatePublicOjLink(ojId)
        val cleanCaption = caption.trim()
        return if (cleanCaption.isNotBlank()) {
            "$cleanCaption\n\nWatch on OJAS: $link"
        } else {
            "Watch this video on OJAS: $link"
        }
    }

    /**
     * Opens native Android system share sheet.
     */
    fun openSystemShareSheet(context: Context, caption: String, ojId: String) {
        if (ojId.isBlank()) return
        val shareText = buildShareText(caption, ojId)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "OJAS Video")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(intent, "Share Video")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Graceful fallback if no sharing application is installed
        }
    }

    /**
     * Safely extracts and validates an OJ ID from incoming Intent Uri.
     */
    fun parseOjIdFromUri(uri: Uri?): String? {
        if (uri == null) return null

        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null

        val extractedId = when {
            // Custom scheme: ojas://oj/{ojId} or ojas://oj?id={ojId}
            scheme == SCHEME_CUSTOM && host == HOST_OJ -> {
                uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: uri.getQueryParameter("id")
                    ?: uri.lastPathSegment
            }

            // Web domain: https://ojas.app/oj/{ojId} or http://ojas.app/oj/{ojId}
            (scheme == "https" || scheme == "http") && (host == WEB_DOMAIN || host == "www.$WEB_DOMAIN") -> {
                val segments = uri.pathSegments
                if (segments.size >= 2 && segments[0].equals("oj", ignoreCase = true)) {
                    segments[1]
                } else {
                    uri.getQueryParameter("id")
                }
            }

            else -> null
        }

        return extractedId?.trim()?.takeIf { isValidOjId(it) }
    }

    /**
     * Validates that the parsed ID matches acceptable identifier formatting.
     */
    private fun isValidOjId(id: String): Boolean {
        if (id.isBlank() || id.length > 128) return false
        // Ensure no path traversal or dangerous characters
        return id.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
}
