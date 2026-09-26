import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

// -Ptarget=windows / linux builds the jar with that system's graphics libraries (bundles for them are made on a Mac).
val target = (findProperty("target") as String?) ?: "current"

val appVersion = "1.4.0"

// APP_VERSION for the settings pane, the ADIF header and the HTTP User-Agent.
val generateBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("version", appVersion)
    outputs.dir(out)
    doLast {
        val f = out.get().file("ru/r3xed/qsolog/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package ru.r3xed.qsolog\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generateBuildInfo)

dependencies {
    implementation(project(":shared"))
    when (target) {
        "windows" -> implementation(compose.desktop.windows_x64)
        "linux" -> implementation(compose.desktop.linux_x64)
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "QSO-LOG"
            packageVersion = appVersion
            description = "Аппаратный журнал QSO с поиском по QRZ.ru"
            vendor = "R3XED"
            // From `suggestRuntimeModules`, plus TLS ciphers for HTTPS and Russian locale data.
            modules(
                "java.instrument", "java.sql", "jdk.security.auth", "jdk.unsupported",
                "java.naming", "java.management", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets",
            )
            macOS {
                bundleID = "ru.r3xed.qsolog"
                // The bundled Java 17 runtime needs macOS 11; Ventura (13) and newer, Intel and Apple Silicon.
                minimumSystemVersion = "11.0"
                iconFile.set(project.file("icons/icon.icns"))
                // Without this key macOS refuses the microphone to the app instead of asking the user.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>QSO-LOG записывает голосовые заметки к связям.</string>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "QSO-LOG"
                shortcut = true
                menu = true
                upgradeUuid = "6f1d0a52-3c3e-4f7e-9a51-5b8c2f0e9a11"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "qso-log"
                debMaintainer = "R3XED <13299424+vladkondratyev@users.noreply.github.com>"
                menuGroup = "HamRadio"
                appCategory = "hamradio"
                shortcut = true
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

// README screenshots with made-up data: docs/screenshots/desktop-*.png
tasks.register<JavaExec>("screenshots") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ru.r3xed.qsolog.ScreenshotsKt")
    args(rootProject.file("docs/screenshots").absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}
