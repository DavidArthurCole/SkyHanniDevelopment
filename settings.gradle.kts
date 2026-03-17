// This is wack, but it seems to be the only surefire way to get IntelliJ
// to actually download sources when you ask it to.
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SkyHanniDevelopment"

include(":detekt")
project(":detekt").name = "detekt"