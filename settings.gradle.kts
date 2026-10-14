pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android (CAT over the radio's USB cable) is published only there.
        maven("https://jitpack.io") { content { includeGroup("com.github.mik3y") } }
    }
}
rootProject.name = "qsolog"
include(":app", ":shared")
// The desktop version is not part of the public repository; build it only where its folder exists.
if (file("desktop").isDirectory) include(":desktop")
