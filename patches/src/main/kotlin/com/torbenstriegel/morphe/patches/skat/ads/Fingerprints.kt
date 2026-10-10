package com.torbenstriegel.morphe.patches.skat.ads

import app.morphe.patcher.Fingerprint

/**
 * The static JNI bridge method the Qt/QML game layer calls whenever a sticky
 * banner ad should be shown. Non-obfuscated and stable across versions.
 */
object ShowBannerAdFingerprint : Fingerprint(
    definingClass = "Lcom/isarinteractive/GameActivity;",
    name = "showBannerAd",
    returnType = "V"
)

/**
 * The static JNI bridge method the Qt/QML game layer calls whenever a
 * fullscreen (interstitial) ad should be shown. Returns whether an ad was
 * actually presented.
 */
object ShowFullscreenAdFingerprint : Fingerprint(
    definingClass = "Lcom/isarinteractive/GameActivity;",
    name = "showFullscreenAd",
    returnType = "Z"
)