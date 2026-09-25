import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

// -Ptarget=windows builds the jar with Windows graphics libraries (used for the Windows bundle made on a Mac).
val target = (findProperty("target") as String?) ?: "current"

dependencies {
    implementation(project(":shared"))
    when (target) {
        "windows" -> implementation(compose.desktop.windows_x64)
        else -> implementation(compose.desktop.currentOs)
    }
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.github.javakeyring:java-keyring:1.0.4")
}

compose.desktop {
    application {
        mainClass = "ru.r3xed.qsolog.MainKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
            obfuscate.set(false)
            optimize.set(false)
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "QSO-LOG"
            packageVersion = "1.1.1"
            description = "Аппаратный журнал QSO с поиском по QRZ.ru"
            vendor = "R3XED"
            // From `suggestRuntimeModules`, plus TLS ciphers for HTTPS and Russian locale data.
            modules(
                "java.instrument", "java.sql", "jdk.security.auth", "jdk.unsupported",
                "java.naming", "java.management", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets",
            )
            macOS {
                bundleID = "ru.r3xed.qsolog"
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "QSO-LOG"
                shortcut = true
                menu = true
                upgradeUuid = "6f1d0a52-3c3e-4f7e-9a51-5b8c2f0e9a11"
            }
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
