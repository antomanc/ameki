package dev.antomanc.ameki.core.data

import android.content.Context
import android.content.pm.PackageManager
import dev.antomanc.ameki.core.model.AndroidAppIdentity
import java.security.MessageDigest

/**
 * Package name and signing-certificate SHA-1 of this build. Google needs both to accept a key
 * restricted to Android apps, and the onboarding guide shows them so the user can paste them
 * into the Cloud console.
 */
object AppIdentity {
    fun of(context: Context): AndroidAppIdentity? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        val sha1 = MessageDigest.getInstance("SHA-1").digest(signer.toByteArray())
        AndroidAppIdentity(context.packageName, sha1.joinToString(":") { "%02X".format(it) })
    }.getOrNull()
}
