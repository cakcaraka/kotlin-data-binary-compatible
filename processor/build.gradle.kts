plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.ksp)
    id("maven-publish")
}

group = "com.cakcaraka.databinarycompatible"


ksp {
    arg("autoserviceKsp.verify", "true")
    arg("autoserviceKsp.verbose", "true")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.ksp.api)
    implementation(libs.auto.service.annotations)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    ksp(libs.auto.service.ksp)
    implementation(project(":annotation"))
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview"
}