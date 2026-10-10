package com.torbenstriegel.morphe.patches.sai.licensecheck

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_SAI

/**
 * Disables the PairIP Play licensing gate in SAI 2.4.2.
 *
 * ## Background (verified on 2.4.2, com.mtv.sai)
 *
 * SAI is wrapped with Google's PairIP "App protection" layer. The manifest entry
 * point is `com.pairip.application.Application`, and both it and
 * `LicenseContentProvider` call `LicenseClient.checkLicense(Context)` at every
 * app start. On a non-Play (sideloaded / patched) install the license response
 * is always NOT_LICENSED, so the app shows the "get the app from the Play Store"
 * dialog (LicenseActivity) and exits — before the real app code ever runs.
 *
 * The fix no-ops `checkLicense` itself, so both entry points fall through and
 * the whole PairIP licensing pipeline is never started: no dialog, no Play
 * Store redirect, no forced exit.
 *
 * `checkLicense` is part of the fixed-name PairIP library bundle, so the
 * un-obfuscated class/method names are stable anchors.
 */
private val LicenseCheckFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("Cannot check license with null context."),
)

@Suppress("unused")
val disableLicenseCheckPatch = bytecodePatch(
    name = "Remove Play Store redirect",
    description = "Removes the PairIP license check that redirects to the Play " +
        "Store and exits on sideloaded installs.",
) {
    compatibleWith(COMPATIBILITY_SAI)

    execute {
        LicenseCheckFingerprint.method.addInstructions(0, "return-void")
    }
}