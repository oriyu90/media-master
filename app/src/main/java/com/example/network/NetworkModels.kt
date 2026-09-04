package com.example.network

import kotlinx.serialization.Serializable

/** Supported network file protocols. */
enum class NetworkProtocol { SMB, WEBDAV }

/**
 * A saved network location. The password is **never** stored here — it lives in
 * [NetworkCredentialStore] (EncryptedSharedPreferences), keyed by [id].
 */
@Serializable
data class NetworkLocation(
    val id: String,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int = 0,
    /** SMB share name, or the first path segment for WebDAV. May be blank. */
    val share: String = "",
    /** Optional sub-path under the share, no leading slash. */
    val basePath: String = "",
    val username: String = "",
) {
    /** Effective port, filling in the protocol default when [port] is 0. */
    val effectivePort: Int
        get() = when {
            port != 0 -> port
            protocol == NetworkProtocol.SMB -> 445
            else -> 443
        }
}

/** One entry in a remote directory listing. */
data class NetworkEntry(
    val name: String,
    /** Path relative to the location root, no leading slash. */
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
)

/** Result of a browse operation, kept deliberately small and immutable. */
sealed interface BrowseUiState {
    data object Idle : BrowseUiState
    data object Loading : BrowseUiState
    data class Ready(
        val location: NetworkLocation,
        val path: String,
        val entries: List<NetworkEntry>,
    ) : BrowseUiState
    data class Error(val message: String) : BrowseUiState
}
