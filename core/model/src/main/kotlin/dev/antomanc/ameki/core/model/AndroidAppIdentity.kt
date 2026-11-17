package dev.antomanc.ameki.core.model

/**
 * Identifies the calling app, so a key restricted to "Android apps" accepts the request.
 * [certSha1] is the signing certificate SHA-1 as hex, colons optional.
 */
data class AndroidAppIdentity(val packageName: String, val certSha1: String)
