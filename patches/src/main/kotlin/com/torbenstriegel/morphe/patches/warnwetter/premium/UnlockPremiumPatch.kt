package com.torbenstriegel.morphe.patches.warnwetter.premium

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_WARNWETTER

/**
 * Unlocks the WarnWetter Vollversion (ad-free, 7-day forecast, radar loop,
 * map products, pollen, coastal/sea texts) without a purchase or promo code.
 *
 * ## Architecture (verified on 5.4.2, versionCode 5425, de.dwd.warnapp)
 *
 * All premium state comes from the non-obfuscated PlanB billing layer
 * `de.dwd.warnapp.util.a0` (Play Billing + promo codes), which persists three
 * SharedPreferences keys:
 *
 *   - "planb.pcode"                 — promo code, read by a0.t()Z
 *   - "planb.purchased"             — Play purchase, read by a0.u()Z
 *   - "planb.purchaseacknowledged"  — acknowledgement only, not a gate
 *
 * `a0.x()Z` combines the first two: it returns true only when NEITHER is set,
 * i.e. "this user is on the free tier". It is the single choke point used by
 * 60 call sites (HomescreenAdapter, MainActivity, O4, push registration, tile
 * manager x). Forcing t() and u() to true makes x() return false everywhere,
 * which is the exact condition the app treats as "premium".
 *
 * `de.dwd.warnapp.util.Product.isFree()` decides whether a product tile may be
 * used at all; premium products are compiled with `isFree = false`. Forcing it
 * to true keeps the premium tiles (Warnmonitor, maps, pollen, ...) available.
 *
 * Promo codes are validated server-side (`PromoTokenVerification` DTO); forcing
 * `isValid()` to true makes the in-app "Promo-Code" dialog accept any code, so
 * the unlock also works through the official UI path if a state refresh is
 * needed after installing.
 *
 * No account, no Play Services purchase and no network call is required.
 */
@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks the WarnWetter Vollversion (ad-free, 7-day forecast, " +
        "radar loop, map products and pollen) without a purchase or account.",
) {
    compatibleWith(COMPATIBILITY_WARNWETTER)

    execute {
        // "has promo code" and "has purchase" both report true -> a0.x() is false.
        HasPromoCodeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )
        IsPurchasedFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )

        // Every product tile counts as included in the free plan.
        ProductIsFreeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )

        // The promo code dialog accepts arbitrary codes.
        PromoCodeValidFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )
    }
}
