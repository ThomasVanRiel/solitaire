import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        // Slow benchmarks only run when asked for: ./gradlew :engine:test -Pbench
        if (!project.hasProperty("bench")) excludeTags("benchmark")
    }
    maxHeapSize = "2g"
    // The shipped deal packs, replayed by ShippedPacksTest.
    val packsDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/deals")
    inputs.dir(packsDir).optional().withPropertyName("packs")
    systemProperty("packs.dir", packsDir.asFile.absolutePath)
    systemProperty("bench.seeds", providers.gradleProperty("benchSeeds").getOrElse("1000"))
    systemProperty("bench.rules", providers.gradleProperty("benchRules").getOrElse("DRAW_1_UNLIMITED,DRAW_3_UNLIMITED"))
    systemProperty("bench.nodes", providers.gradleProperty("benchNodes").getOrElse("2000000"))
    testLogging {
        events("failed")
        showStandardStreams = project.hasProperty("bench")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
