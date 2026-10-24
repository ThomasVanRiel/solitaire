pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "winnable-patience"

include(":engine", ":dealgen")

// The Android app needs the Android SDK. On machines without one (for example a plain JVM box
// that only runs engine tests), :app is left out so :engine and :dealgen still build.
fun androidSdkPresent(): Boolean {
    val fromEnv = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").mapNotNull { System.getenv(it) }
    val localProps = file("local.properties")
    val fromLocal = if (localProps.exists()) {
        localProps.readLines().firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")?.trim()
    } else null
    return (fromEnv + listOfNotNull(fromLocal)).any { file(it).isDirectory }
}

if (androidSdkPresent()) {
    include(":app")
} else {
    logger.warn("No Android SDK found (ANDROID_HOME or sdk.dir in local.properties): skipping :app")
}
