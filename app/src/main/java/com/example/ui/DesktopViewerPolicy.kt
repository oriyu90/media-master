package com.example.ui

/**
 * v1.14.0: desktop media presentation policy.
 *
 * The Library is a gallery: media opened from it must fill the whole desktop
 * window (sidebar / tab strip / footer / mini-player folded away), and the
 * editor reached from that viewer must stay full-window. Explorer tabs keep
 * the shell until the user presses the overlay fullscreen button, which then
 * collapses the same shell.
 *
 * This file is a pure seam so the routing rules are unit-tested without
 * Compose; the shell itself only reads the result.
 */
internal val MEDIA_PRESENTATION_PREFIXES = listOf(
    // NavController reports destination *patterns*: "viewer/{path}",
    // "imageEditor/{uriString}", "videoEditor/{uriString}".
    "viewer/",
    "imageEditor/",
    "videoEditor/",
)

/** True when [route] is a media presentation destination (viewer or editor). */
internal fun isMediaPresentationRoute(route: String?): Boolean {
    val value = route ?: return false
    return MEDIA_PRESENTATION_PREFIXES.any { value.startsWith(it) }
}

/**
 * Collapse the desktop shell only when the viewer asked for it *and* the
 * current destination is actually a media presentation route. Gating on the
 * route is what prevents a stuck full-window shell after leaving the viewer
 * (e.g. Back to the Library) or after a stale process restore.
 */
internal fun shouldCollapseDesktopShell(requested: Boolean, route: String?): Boolean =
    requested && isMediaPresentationRoute(route)
