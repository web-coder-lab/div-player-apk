package com.divintegrity.player

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** Phase 3 — passive read-only signals only (no game touch) */
object SignalCollector {
    fun collect(ctx: Context): Pair<List<Map<String, Any?>>, Map<String, Any?>> {
        val signals = mutableListOf<Map<String, Any?>>()
        val device = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            "fingerprint" to Build.FINGERPRINT,
            "hardware" to Build.HARDWARE,
            "product" to Build.PRODUCT,
            "tags" to Build.TAGS
        )
        signals += mapOf("type" to "build", "payload" to device)

        val fp = (Build.FINGERPRINT + Build.HARDWARE + Build.PRODUCT + Build.MODEL).lowercase()
        if (listOf("generic", "emulator", "goldfish", "ranchu", "sdk_gphone", "vbox").any { fp.contains(it) }) {
            signals += mapOf(
                "type" to "emulator",
                "payload" to mapOf("suspect" to true, "fingerprint" to Build.FINGERPRINT)
            )
        }

        // Root heuristic from build tags only (no shell)
        if ((Build.TAGS ?: "").contains("test-keys") || fp.contains("userdebug")) {
            signals += mapOf(
                "type" to "root_heuristic",
                "payload" to mapOf("suspect" to true, "flags" to listOf("test-keys_or_userdebug"))
            )
        }

        try {
            val pm = ctx.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val names = apps.take(500).map { it.packageName }
            signals += mapOf("type" to "package_list", "payload" to mapOf("packages" to names))

            val riskKeys = listOf(
                "xposed", "magisk", "frida", "lsposed", "gameguardian", "cheat",
                "substrate", "edxposed", "superuser", "noshufou", "chainfire"
            )
            val risk = names.filter { n -> riskKeys.any { n.lowercase().contains(it) } }
            if (risk.isNotEmpty()) {
                signals += mapOf(
                    "type" to "package_list",
                    "payload" to mapOf("packages" to risk, "flagged" to true)
                )
            }
        } catch (_: Exception) {
            signals += mapOf("type" to "package_list", "payload" to mapOf("error" to "unavailable"))
        }

        signals += mapOf(
            "type" to "root_heuristic",
            "payload" to mapOf("tags" to listOf("passive"), "buildTags" to (Build.TAGS ?: ""))
        )

        return signals to device
    }
}
