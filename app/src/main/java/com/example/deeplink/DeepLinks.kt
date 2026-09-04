package com.example.deeplink

import android.content.Intent
import android.net.Uri

/**
 * External-access surface for Media Master.
 *
 * Other apps can open a specific Media Master feature in two ways:
 *
 *  1. The dedicated `mediamaster://` scheme — an explicit, documented per-feature
 *     entry point (NOT marked BROWSABLE, so arbitrary web pages cannot trigger it):
 *
 *       mediamaster://home
 *       mediamaster://library
 *       mediamaster://audio
 *       mediamaster://documents
 *       mediamaster://manage
 *       mediamaster://apps
 *       mediamaster://clean
 *       mediamaster://settings
 *       mediamaster://browse?path=/storage/emulated/0/Download
 *       mediamaster://edit/image?uri=<content-uri>
 *       mediamaster://edit/video?uri=<content-uri>
 *
 *  2. Standard Android intents, so Media Master shows up in the system
 *     "Open with" / "Edit with" chooser:
 *
 *       ACTION_EDIT  of an image       -> image editor
 *       ACTION_EDIT  of a video        -> video editor
 *       ACTION_VIEW  of image/video/audio -> library (targeted single-file
 *                                          viewing for external URIs arrives in
 *                                          a later phase once the viewer accepts
 *                                          a stand-alone URI)
 *       ACTION_VIEW  of a directory    -> file browser at that path
 *
 * [resolve] converts an incoming [Intent] into an internal navigation route
 * string, or `null` when the request is not understood. Callers must treat
 * `null` as "just open the app normally" — never crash.
 *
 * Destructive operations (delete, uninstall, restore-from-backup) are
 * intentionally NOT reachable from an external intent; they always require an
 * explicit in-app confirmation.
 */
object DeepLinks {

    const val SCHEME = "mediamaster"

    /** Routes that take no arguments and can be opened directly by host name. */
    private val SIMPLE_HOSTS = setOf(
        "home", "library", "audio", "documents", "manage", "apps", "clean", "settings"
    )

    /** Resolve a full [Intent] (cold start or onNewIntent) to an internal route, or null. */
    fun resolve(intent: Intent?): String? {
        if (intent == null) return null
        val data = intent.data
        val action = intent.action
        val type = intent.type

        // 1. Our own scheme.
        if (data != null && data.scheme.equals(SCHEME, ignoreCase = true)) {
            return resolveAppScheme(data)
        }

        // 2. Standard system intents.
        return when (action) {
            Intent.ACTION_EDIT -> when {
                type?.startsWith("image/") == true && data != null -> route("imageEditor", data.toString())
                type?.startsWith("video/") == true && data != null -> route("videoEditor", data.toString())
                else -> null
            }
            Intent.ACTION_VIEW -> when {
                type == "vnd.android.document/directory" -> {
                    val path = data?.path
                    if (!path.isNullOrBlank()) "file_browser?path=${Uri.encode(path)}" else "manage"
                }
                type?.startsWith("image/") == true ||
                    type?.startsWith("video/") == true ||
                    type?.startsWith("audio/") == true -> "library"
                else -> null
            }
            else -> null
        }
    }

    private fun resolveAppScheme(uri: Uri): String? {
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host in SIMPLE_HOSTS -> host
            host == "browse" -> {
                val path = uri.getQueryParameter("path")
                if (!path.isNullOrBlank() && path.startsWith("/")) {
                    "file_browser?path=${Uri.encode(path)}"
                } else {
                    "manage"
                }
            }
            host == "edit" -> {
                val kind = uri.pathSegments.firstOrNull()?.lowercase()
                val target = uri.getQueryParameter("uri")?.takeIf { it.isNotBlank() } ?: return null
                when (kind) {
                    "image" -> route("imageEditor", target)
                    "video" -> route("videoEditor", target)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun route(base: String, arg: String) = "$base/${Uri.encode(arg)}"
}
