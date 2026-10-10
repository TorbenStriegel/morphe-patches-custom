package com.torbenstriegel.morphe.patches.sai.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_SAI

/**
 * Unlocks SAI Split APKs Installer premium (backup / themes) on the com.mtv.sai line.
 *
 * ## Architecture (verified on 2.4.2, versionCode 6x)
 *
 * The app uses the RevenueCat SDK with entitlement id "SAI Premium", but the SDK
 * is fully R8-obfuscated INTO the app (no com/revenuecat class names survive) —
 * legacy fingerprints on RevenueCat library methods therefore never match.
 *
 * The complete premium logic lives in exactly three obfuscated classes that
 * reference the string "SAI Premium" (pj0, qj0, tj0):
 *
 *   pj0.invoke(Object)Object                — 1-arg entitlement lambda: reads
 *                                              EntitlementInfo.isActive and pushes
 *                                              the boolean into the UI LiveData state.
 *   qj0.invoke(Object,Object)Object         — 2-arg entitlement lambda: same check,
 *                                              returns the active-entitlement map.
 *   tj0.h(CustomerInfo)V                    — state writer: persists the entitlement
 *                                              state on every update.
 *
 * All three read the flag via `iget-boolean v0, v0, Lxa2;->b:Z` (obfuscated
 * EntitlementInfo.isActive) followed by `const/4 vX, 0x1` and an `if-ne` that
 * branches to the "not entitled" path.
 *
 * ## Strategy
 *
 *   - In both lambdas the `if-ne` after the isActive read is REMOVED, so the
 *     success path is always taken (premium = true, pushed to the UI state).
 *   - The state writer is no-oped so no "not premium" state can ever be persisted.
 *
 * Fingerprints are anchored on the unique string "SAI Premium" + signature shape,
 * never on obfuscated class/member names, so they survive app updates as long as
 * the entitlement id stays "SAI Premium".
 */
private val PremiumLambda1ArgFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("SAI Premium"),
)

private val PremiumLambdaFingerprint = Fingerprint(
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    strings = listOf("SAI Premium"),
)

private val PremiumStateWriterFingerprint = Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    strings = listOf("SAI Premium"),
)

@Suppress("unused")
val saiPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks SAI Split APKs Installer premium (backups, themes) by " +
        "forcing the \"SAI Premium\" entitlement state to active.",
) {
    compatibleWith(COMPATIBILITY_SAI)

    execute {
        // Both entitlement lambdas: drop the "not active" branch after reading
        // EntitlementInfo.isActive (obfuscated field Lxa2;->b:Z), so the active
        // path with premium=true is always taken.
        for (fp in listOf(PremiumLambda1ArgFingerprint, PremiumLambdaFingerprint)) {
            fp.method.let { m ->
                val insns = m.implementation!!.instructions
                val isActiveRead = insns.indexOfFirst { ins ->
                    ins.opcode == Opcode.IGET_BOOLEAN &&
                        (ins as? ReferenceInstruction)?.reference?.toString()
                            ?.contains("Lxa2;->b:Z") == true
                }
                if (isActiveRead >= 0 && isActiveRead + 2 < insns.size) {
                    m.removeInstruction(isActiveRead + 2) // the if-ne -> always active path
                } else {
                    error("SAI premium lambda pattern not found in ${m.definingClass}")
                }
            }
        }

        // No-op the state writer: no "not premium" state is ever persisted.
        PremiumStateWriterFingerprint.method.addInstructions(0, "return-void")
    }
}