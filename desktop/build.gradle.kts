import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "br.com.monitordenoticias"
version = "4.0.2"

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("../app/src/main/java")
            kotlin.exclude(
                "br/com/monitordenoticias/desktop/PolishedDashboardMain.kt",
                "br/com/monitordenoticias/desktop/DashboardV3Main.kt",
                "br/com/monitordenoticias/android/BackgroundMonitor.kt",
                "br/com/monitordenoticias/android/DemandMonitorWorker.kt",
                "br/com/monitordenoticias/android/MainActivity.kt",
                "br/com/monitordenoticias/android/MainActivityV24.kt",
                "br/com/monitordenoticias/android/MainActivityV25.kt",
                "br/com/monitordenoticias/android/MainActivityV27.kt",
                "br/com/monitordenoticias/android/MainActivityV28.kt",
                "br/com/monitordenoticias/android/MonitorViewModel.kt",
                "br/com/monitordenoticias/android/MonitorWorker.kt",
                "br/com/monitordenoticias/android/NewsDb.kt",
                "br/com/monitordenoticias/android/NotificationHelper.kt",
                "br/com/monitordenoticias/android/V27LayoutCompat.kt",
                "br/com/monitordenoticias/android/V30Screens.kt",
                "br/com/monitordenoticias/android/VideoBackgroundMonitor.kt",
                "br/com/monitordenoticias/android/VideoDb.kt",
                "br/com/monitordenoticias/android/VideoMonitorWorker.kt",
                "br/com/monitordenoticias/android/VideoScreenV29.kt",
                "br/com/monitordenoticias/android/VideoViewModel.kt"
            )
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.json:json:20240303")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("net.sf.kxml:kxml2:2.3.0")
}

compose.desktop {
    application {
        mainClass = "br.com.monitordenoticias.desktop.DashboardV4MainKt"
        nativeDistributions {
            modules("java.sql", "java.instrument", "jdk.unsupported")
            targetFormats(TargetFormat.Msi)
            packageName = "MonitorDeNoticias"
            packageVersion = "4.0.2"
            description = "Monitor de Notícias v4.0.2 para Windows"
            vendor = "Monitor de Notícias"
            windows {
                menuGroup = "Monitor de Notícias"
                shortcut = false
                console = false
            }
        }
    }
}
