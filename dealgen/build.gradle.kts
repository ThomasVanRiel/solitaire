import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

application {
    mainClass = "io.github.thomasvanriel.winnable.dealgen.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}

dependencies {
    implementation(project(":engine"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
