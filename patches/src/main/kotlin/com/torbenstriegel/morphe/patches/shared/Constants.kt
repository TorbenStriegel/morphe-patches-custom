package com.torbenstriegel.morphe.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_WETTERRADAR = Compatibility(
        name = "Weather&Radar",
        packageName = "de.wetteronline.wetterapp",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x10658E,
        targets = listOf(
            AppTarget(
                version = "2026.19"
            )
        )
    )

    val COMPATIBILITY_SKAT = Compatibility(
        name = "Skat",
        packageName = "com.isarinteractive.skat.android.ad",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x870800,
        targets = listOf(
            AppTarget(
                version = "19.2.4"
            )
        )
    )

    val COMPATIBILITY_ONEFOOTBALL = Compatibility(
        name = "OneFootball",
        packageName = "de.motain.iliga",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x00C853,
        targets = listOf(
            AppTarget(
                version = "15.145.0",
                versionCode = 1044999406
            )
        )
    )

    val COMPATIBILITY_SAI = Compatibility(
        name = "SAI Split APKs Installer",
        packageName = "com.mtv.sai",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1976D2,
        targets = listOf(
            AppTarget(
                version = "2.4.2"
            )
        )
    )

    val COMPATIBILITY_TORALARM = Compatibility(
        name = "Toralarm",
        packageName = "com.eisterhues_media_2",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x73B285,
        targets = listOf(
            AppTarget(
                version = "7.6.3",
                versionCode = 202609180
            )
        )
    )

    val COMPATIBILITY_NTV = Compatibility(
        name = "ntv",
        packageName = "de.lineas.lit.ntv.android",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFFCC00,
        targets = listOf(
            AppTarget(
                version = "8.5.7.1",
                versionCode = 586
            )
        )
    )
}