package com.torbenstriegel.morphe.patches.sai.premium

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
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
 * Ersetzt JEDE `iget-boolean vX, vY, Lxa2;->b:Z` (Entitlement.isActive-Read)
 * durch `const/4 vX, 0x1`, sodass der übrige Kontrollfluss UNVERÄNDERT
 * bleibt und immer der Premium-Zweig genommen wird.
 *
 * 2026-10-10 Fix: removeNotActiveBranch (if-Entfernung nach jedem Read) war
 * zu aggressiv - tj0.h hat ZWEI b:Z-Reads und der Folge-if des zweiten ist
 * Teil eines UNBETEILIGTEN Zweigs (:cond_1/:cond_8). Drei entfernte ifs rissen
 * den Kontrollfluss in sj0 (Coroutine) auf -> ClassCastException
 * "tj0 cannot be cast to dy7" beim App-Start. Read-Ersetzung ist
 * register-sicher (dest-Register bleibt definiert, src wird nur nicht mehr
 * benutzt) und laesst alle Labels/Branches intakt.
 */
private fun forceIsActive(m: MutableMethod) {
    val insns = m.implementation!!.instructions.toList()
    var n = 0
    for (i in insns.indices) {
        val ins = insns[i]
        if (ins.opcode.name == "iget-boolean" &&
            (ins as? ReferenceInstruction)?.reference?.toString()?.contains("Lxa2;->b:Z") == true
        ) {
            val reg = (ins as? OneRegisterInstruction)?.registerA ?: continue
            m.replaceInstruction(i, "const/4 v$reg, 0x1")
            n++
        }
    }
    if (n == 0) error("SAI premium isActive pattern not found in ${m.definingClass}")
}

@Suppress("unused")
val saiPremiumPatch = bytecodePatch(
    name = "Unlock Premium",
    description = "Unlocks SAI Split APKs Installer premium (backups, themes) by " +
        "forcing the \"SAI Premium\" entitlement state to active.",
) {
    compatibleWith(COMPATIBILITY_SAI)

    execute {
        // Lambda 1 + 2: isActive-Reads zu true zwingen -> immer premium
        // melden, Kontrollfluss unveraendert.
        forceIsActive(PremiumLambda1ArgFingerprint.method)
        forceIsActive(PremiumLambdaFingerprint.method)

        // Central calculator: keep it running but always yield "active".
        // 2026-10-10 (zweiter Fix): ALLE b:Z-Reads zu const/4 0x1 und KEINEN
        // v1-Fallback-Flip mehr. Der naechste Fehler war (Stacktrace bewiesen):
        // v1 wird am Ende von tj0.h als Coroutine-Typ-Index an
        // sj0.<init>(Object;Z;Lge1;I) uebergeben -> v1: 0->1 verschob den
        // packed-switch in sj0.create von pswitch_4 (check-cast Ltj0;) auf
        // pswitch_0 (check-cast Ldy7;) -> ClassCastException "tj0 cannot be
        // cast to dy7" beim App-Start. Der v1-Fallback ist also NICHT nur ein
        // Premium-Bool, sondern hat die Doppelfunktion Coroutine-Klasse.
        // Nur die b:Z-Reads zwingen reicht fuer den Premium-Pfad; der
        // ent-itlement-missing-Pfad bleibt original (schadet nicht).
        forceIsActive(PremiumStateWriterFingerprint.method)
    }
}