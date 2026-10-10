package com.torbenstriegel.morphe.patches.toralarm.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_TORALARM

/**
 * Unlocks Toralarm premium (tor/news content behind the paywall).
 *
 * ## Architecture (verified on 7.6.3, versionCode 202609180)
 *
 * The whole premium state lives in ONE class that is NOT obfuscated:
 * com.eisterhues_media_2.core.premium.PremiumManager (only its private method
 * names are R8-shortened to single letters, which is why the fingerprints are
 * anchored on the unique SharedPreferences keys instead).
 *
 *   PremiumManager.R()Z  — "isPremium": returns force_premium_key (F()) or the
 *                           persisted subscription flag "subactive".
 *   PremiumManager.W(Z)V — "setActivePremium": writes "subactive" + emits state.
 *   PremiumManager.X(Z)V — "setActivePure": writes "pure_subscription".
 *
 * "subactive" and "pure_subscription" are the only SharedPreferences keys used
 * for the subscription state and each appears in exactly one reader and one
 * writer method, so they are stable anchors across app updates.
 */
private val IsPremiumFingerprint = Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("subactive"),
)

private val SubActiveWriterFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("subactive"),
)

private val PureSubscriptionWriterFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("pure_subscription"),
)

@Suppress("unused")
val toralarmPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks Toralarm premium content by forcing the subscription " +
        "state to active.",
) {
    compatibleWith(COMPATIBILITY_TORALARM)

    execute {
        // isPremium always reports true.
        IsPremiumFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )

        // Writers persist and emit "active" no matter what the billing result was.
        SubActiveWriterFingerprint.method.addInstructions(0, "const/4 p1, 0x1")
        PureSubscriptionWriterFingerprint.method.addInstructions(0, "const/4 p1, 0x1")
    }
}