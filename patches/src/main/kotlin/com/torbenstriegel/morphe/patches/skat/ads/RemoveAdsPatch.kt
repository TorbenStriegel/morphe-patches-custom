package com.torbenstriegel.morphe.patches.skat.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_SKAT

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes banner and fullscreen (interstitial) ads from the game. Interstitials are reported as shown and closed immediately so the Qt game flow is unaffected. Works without an account."
) {
    compatibleWith(COMPATIBILITY_SKAT)

    execute {
        // Never show the sticky banner: an immediate return is enough, nothing
        // is started or attached.
        ShowBannerAdFingerprint.method.addInstructions(
            0,
            """
                return-void
            """.trimIndent()
        )

        // Report the interstitial as successfully shown and immediately closed,
        // so the native (Qt) layer proceeds as if an ad had finished playing,
        // without ever presenting one (no fullscreenPlacement.show() call).
        ShowFullscreenAdFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lcom/isarinteractive/NativeCallHelper;->callFullscreenAdClosed()V
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}