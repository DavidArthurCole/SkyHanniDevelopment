plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SkyHanniIntelliJPlugin"

include(":detekt")
project(":detekt").name = "detekt"