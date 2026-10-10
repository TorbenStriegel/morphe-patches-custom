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
}