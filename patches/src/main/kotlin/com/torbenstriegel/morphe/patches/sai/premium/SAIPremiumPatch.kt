package com.torbenstriegel.morphe.patches.sai.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.torbenstriegel.morphe.patches.shared.Constants.COMPATIBILITY_SAI

/**
 * Unlocks SAI Split APKs Installer premium (backup / themes) on the com.mtv.sai line.
 *
 * ## Architecture (verified on 2.4.2, versionCode 6x)
 *
 * SAI 2.4.2 uses RevenueCat (entitlement id "SAI Premium"), fully R8-obfuscated
 * INTO the app (no com/revenuecat names survive) — legacy fingerprints on
 * RevenueCat library methods therefore never match. The complete premium logic
 * lives in exactly three obfuscated classes that reference the string
 * "SAI Premium" (pj0, qj0, tj0):
 *
 *   pj0.invoke(Object)Object          — 1-arg entitlement lambda
 *   qj0.invoke(Object,Object)Object   — 2-arg entitlement lambda
 *   tj0.h(CustomerInfo)V              — CENTRAL premium calculator + state
 *                                       writer: reads EntitlementInfo.isActive
 *                                       (Lxa2;->b:Z), computes the active flag
 *                                       and pushes it into the app state the
 *                                       paywalls observe.
 *
 * ## Why "no-op the state writer" FAILED (v1.2.0 .. v1.4.0)
 *
 * tj0.h is not a passive writer: when it is no-oped, the observed premium state
 * stays on its initial default (false) forever, so the backup paywall ("Creating
 * backups is available only to users with an active subscription") still
 * appears. It must keep RUNNING but always produce the "active" outcome:
 *
 *   1. `const/4 v1, 0x0` right after the cast to Lxa2; sets the FALLBACK value
 *      used when the entitlement is null -> flip to 0x1 (default: premium).
 *   2. The `if-ne`/`if-eqz` branches after every `iget-boolean vX, vY, Lxa2;->b:Z`
 *      (the "not active" exits) are removed, so the active path is always taken.
 *
 * pj0/qj0 use the same if-after-isActive pattern and are patched the same way,
 * so all call paths report premium.
 *
 * ## Runtime pitfall (critical!)
 *
 * The patcher runtime serves instruction objects from its bundled multidexlib2
 * fork, whose Opcode enum differs from the dexlib2 enum the patch sources
 * compile against: `opcode.name` returns the SMALI-style lowercase name
 * ("iget-boolean", "if-ne", "check-cast", "const/4"), not the enum constant
 * name ("IGET_BOOLEAN"). Comparing enum instances or uppercase names is always
 * FALSE and the patch dies with "pattern not found" although the smali shows
 * the read. Always compare opcode NAMES in their lowercase smali form.
 * (ReferenceInstruction casts DO work and yield the field reference string.)
 *
 * Fingerprints are anchored on the unique string "SAI Premium" + signature shape,
 * never on obfuscated class/member names.
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

/**
 * Removes the first IF_* branch after each `iget-boolean ... Lxa2;->b:Z` read,
 * so the "not active" exit of the method can never be taken.
 */
private fun removeNotActiveBranch(m: MutableMethod) {
    val insns = m.implementation!!.instructions.toList()
    val ifIndices = mutableListOf<Int>()
    for (i in insns.indices) {
        val ins = insns[i]
        if (ins.opcode.name == "iget-boolean" &&
            (ins as? ReferenceInstruction)?.reference?.toString()?.contains("Lxa2;->b:Z") == true
        ) {
            for (j in i + 1 until insns.size) {
                if (insns[j].opcode.name.startsWith("if-")) {
                    ifIndices.add(j)
                    break
                }
            }
        }
    }
    if (ifIndices.isEmpty()) {
        error("SAI premium isActive pattern not found in ${m.definingClass}")
    }
    ifIndices.sortedDescending().forEach { m.removeInstruction(it) }
}

@Suppress("unused")
val saiPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks SAI Split APKs Installer premium (backups, themes) by " +
        "forcing the \"SAI Premium\" entitlement state to active.",
) {
    compatibleWith(COMPATIBILITY_SAI)

    execute {
        // Lambda 1 + 2: drop the "not active" branch -> always report premium.
        removeNotActiveBranch(PremiumLambda1ArgFingerprint.method)
        removeNotActiveBranch(PremiumLambdaFingerprint.method)

        // Central calculator: keep it running but always yield "active".
        val h = PremiumStateWriterFingerprint.method
        val hInsns = h.implementation!!.instructions.toList()
        // Flip the fallback `const/4 v1, 0x0` (right after the cast to Lxa2;) to 0x1,
        // so the "entitlement missing" path also yields premium.
        val castIdx = hInsns.indexOfFirst {
            it.opcode.name == "check-cast" &&
                (it as? ReferenceInstruction)?.reference?.toString()?.contains("Lxa2;") == true
        }
        if (castIdx < 0) error("SAI premium cast target not found in ${h.definingClass}")
        val fallbackIdx = hInsns.indices.firstOrNull { it > castIdx &&
            hInsns[it].opcode.name == "const/4" &&
            hInsns[it] is NarrowLiteralInstruction &&
            (hInsns[it] as NarrowLiteralInstruction).wideLiteral == 0L &&
            (hInsns[it] as OneRegisterInstruction).registerA == 1 }
        if (fallbackIdx == null) error("SAI premium fallback const not found in ${h.definingClass}")
        h.removeInstruction(fallbackIdx)
        h.addInstructions(fallbackIdx, "const/4 v1, 0x1")
        // Drop the "not active" exits of the calculator itself.
        removeNotActiveBranch(h)
    }
}