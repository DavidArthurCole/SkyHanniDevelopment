import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask

plugins {
    kotlin("jvm")
    alias(libs.plugins.devtoolsKsp)
    alias(libs.plugins.detekt)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.detekt.api)
    ksp(libs.autoservice.ksp)
    implementation(libs.autoservice.annotations)
    implementation(libs.detektrules.ktlint)
    testImplementation("io.kotest:kotest-assertions-core:6.1.10")
    testImplementation(libs.detekt.test)
    detektPlugins(libs.detektrules.authors)
    detektPlugins(libs.detektrules.ktlint)
}

tasks.withType<Detekt>().configureEach {
    onlyIf { false }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    onlyIf { false }
}
