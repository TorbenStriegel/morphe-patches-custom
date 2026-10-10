group = "com.torbenstriegel"

patches {
    about {
        name = "TorbenStriegel Patches"
        description = "Patches for apps I use: Weather&Radar, Skat and more to come."
        source = "git@github.com:TorbenStriegel/morphe-patches-custom.git"
        author = "Torben Striegel"
        contact = "https://github.com/TorbenStriegel"
        website = "https://github.com/TorbenStriegel/morphe-patches-custom"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
