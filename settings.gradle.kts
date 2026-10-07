pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // fabric-loom-remap for the obfuscated versions, fabric-loom for 26.1+
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

stonecutter {
    create(rootProject) {
        // 1.21.8 also runs on 1.21.6-1.21.7, 1.21.10 on 1.21.9, 26.1.2 on 26.1-26.1.1
        // 1.21.5, 1.21.8 and 1.21.10 are added back once they are ported
        versions("1.21.11", "26.1.2", "26.2", "26.3")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "autyism-litematica-enhancement"
