package com.torbenstriegel.morphe.patches.warnwetter.premium

import app.morphe.patcher.Fingerprint

/**
 * WarnWetter is R8-obfuscated: the billing manager is `de.dwd.warnapp.util.a0`,
 * its methods are single letters (`t`, `u`, `x`). The SharedPreferences keys of
 * the PlanB billing layer are the only stable anchors and each of them is read
 * by exactly one boolean getter.
 *
 *   a0.t()Z — contains("planb.pcode")     : a promo code is stored
 *   a0.u()Z — contains("planb.purchased") : a Play purchase was recorded
 *   a0.x()Z — !t() && !u()                : "user is on the free tier"
 *
 * `x()` is the choke point: 60 call sites (homescreen adapters, O4, MainActivity,
 * push registration, tile manager). Forcing `t()` and `u()` to true therefore
 * flips every `isFreeUser` gate without having to touch the obfuscated `x()`.
 */
object HasPromoCodeFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("planb.pcode"),
)

object IsPurchasedFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("planb.purchased"),
)

/**
 * Which home screen tiles/products are usable without a purchase. Premium
 * products (maps, pollen, sea/coast texts, ...) have `isFree = false`, so a
 * user without an entitlement cannot even add them to the homescreen.
 */
object ProductIsFreeFingerprint : Fingerprint(
    definingClass = "Lde/dwd/warnapp/util/Product;",
    name = "isFree",
    returnType = "Z",
)

/**
 * Result of the promo code verification request (`PromoTokenVerification` DTO
 * posted to the PlanB backend). This class is not obfuscated.
 */
object PromoCodeValidFingerprint : Fingerprint(
    definingClass = "Lde/dwd/warnapp/model/PromoTokenVerification;",
    name = "isValid",
    returnType = "Z",
)
