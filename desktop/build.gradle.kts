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
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-tiff:3.12.0")
    implementation("net.sf.kxml:kxml2:2.3.0")

    // JavaFX é compartilhado pelo login Globoplay e pelo preview embutido do Editor de Vídeo.
    implementation("org.openjfx:javafx-base:21.0.10:win")
    implementation("org.openjfx:javafx-graphics:21.0.10:win")
    implementation("org.openjfx:javafx-controls:21.0.10:win")
    implementation("org.openjfx:javafx-swing:21.0.10:win")
    implementation("org.openjfx:javafx-web:21.0.10:win")
    implementation("org.openjfx:javafx-media:21.0.10:win")
}

compose.desktop {
    application {
        mainClass = "br.com.monitordenoticias.desktop.DashboardV5MainKt"
        nativeDistributions {
            modules("java.sql", "java.instrument", "jdk.unsupported", "java.net.http", "jdk.jsobject")
            targetFormats(TargetFormat.Msi)
            packageName = "MonitorDeNoticias"
            packageVersion = "4.0.2"
            description = "Monitor de Notícias v4.0.2 para Windows"
            vendor = "Monitor de Notícias"
            windows {
                iconFile.set(project.file("src/main/resources/monitor-icon.ico"))
                menuGroup = "Monitor de Notícias"
                shortcut = false
                console = false
            }
        }
    }
}
