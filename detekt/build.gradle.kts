import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

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
    implementation(libs.detekt.formatting)
    testImplementation("io.kotest:kotest-assertions-core:6.1.9")
    testImplementation(libs.detekt.test)
    detektPlugins(libs.detekt.rules.authors)
}

tasks.withType<Detekt>().configureEach {
    onlyIf { false }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    onlyIf { false }
}
