package com.torbenstriegel.morphe.patches.onefootball.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_ONEFOOTBALL

// OneFootball's app code is obfuscated, but every ad runs through Google Mobile Ads
// (com.google.android.gms.ads), whose class and method names are stable library API.
// This patch hooks that surface: the SDK never initializes, direct loads become no-ops
// and the app-open preloader reports failure.
// Written without app.morphe.util.returnEarly so it also runs on patcher-runtime 1.14.1.
private const val GMA = "Lcom/google/android/gms/ads/"

private val AD_CLASSES = listOf(
    "BaseAdView",
    "AdView",
    "AdLoader",
    "interstitial/InterstitialAd",
    "rewarded/RewardedAd",
    "rewardedinterstitial/RewardedInterstitialAd",
    "appopen/AppOpenAd",
    "admanager/AdManagerAdView",
    "admanager/AdManagerInterstitialAd",
)

private fun BytecodePatchContext.methods(relative: String): Set<MutableMethod> =
    mutableClassDefByOrNull(GMA + relative + ";")?.methods.orEmpty()

@Suppress("unused")
val disableAdsPatch = bytecodePatch(
    name = "Disable ads",
    description = "Disables banner, interstitial, native, app-open and rewarded ads."
) {
    compatibleWith(COMPATIBILITY_ONEFOOTBALL)

    execute {
        // Never initialize the ads SDK.
        methods("MobileAds")
            .filter { it.implementation != null }
            .filter { it.name == "initialize" }
            .forEach { it.addInstructions(0, "return-void") }

        // Direct loads become no-ops.rest
        for (relative in AD_CLASSES) {
            methods(relative)
                .filter { it.implementation != null }
                .filter { (it.name == "load" || it.name == "loadAd") && it.returnType == "V" }
                .forEach { it.addInstructions(0, "return-void") }
        }

        // Nothing is preloaded for app-open ads:report false immediately.
        methods("appopen/AppOpenAdPreloader")
            .filter { it.implementation != null }
            .filter { it.name == "start" && it.returnType == "Z" }
            .forEach {
                it.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent()
                )
            }
    }
}