package com.torbenstriegel.morphe.patches.skat.licensecheck

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_SKAT

/**
 * Disables the PairIP Play licensing gate in Skat 19.2.4.
 *
 * ## Background (verified on 19.2.4, com.isarinteractive.skat.android.ad)
 *
 * Skat is wrapped with Google's PairIP "App protection" layer (same library
 * bundle as SAI). The DEX contains `com.pairip.application.Application`,
 * `com.pairip.licensecheck.LicenseClient`, `LicenseActivity` and
 * `LicenseContentProvider`, and the `checkLicense(Context)V` entry is called
 * from `Application.attachBaseContext` and the content provider on app start.
 * On a non-Play (sideloaded / patched) install the license response is always
 * NOT_LICENSED, so the app shows the "get the app from the Play Store" dialog
 * (LicenseActivity) and exits.
 *
 * The fix no-ops `checkLicense` itself, so both entry points fall through and
 * the PairIP licensing pipeline is never started: no dialog, no Play Store
 * redirect, no forced exit. `checkLicense` is part of the fixed-name PairIP
 * library bundle, so the un-obfuscated class/method names are stable anchors.
 *
 * Note: Skat's premium features are native (Qt6/QML C++), which Morphe cannot
 * patch — this patch only removes the Play Store redirect gate.
 */
private val LicenseCheckFingerprint = Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "checkLicense",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("Cannot check license with null context."),
)

@Suppress("unused")
val removePlayStoreRedirectPatch = bytecodePatch(
    name = "Remove Play Store redirect",
    description = "Removes the PairIP license check that redirects to the Play " +
        "Store and exits on sideloaded installs.",
) {
    compatibleWith(COMPATIBILITY_SKAT)

    execute {
        LicenseCheckFingerprint.method.addInstructions(0, "return-void")
    }
}