import java.util.Properties

plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

fun resolveBuildPython(): String {
    for (candidate in listOf("D:/Python3.12/python.exe", "python3.12", "python3", "python")) {
        try {
            val process = ProcessBuilder(candidate, "-c",
                "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)")
                .redirectErrorStream(true)
                .start()
            if (process.waitFor() == 0) {
                return candidate
            }
        } catch (_: Exception) {
            // Try the next interpreter.
        }
    }
    error("Python 3.12 is required to build RelayChat")
}

val selectedBuildPython = resolveBuildPython()

val versionProperties = Properties().apply {
    val versionFile = rootProject.file("version.properties")
    require(versionFile.isFile) {
        "Missing version.properties at ${versionFile.absolutePath}"
    }
    versionFile.inputStream().use { input -> load(input) }
}
val appVersionCode = versionProperties.getProperty("versionCode")?.toIntOrNull()
    ?: error("version.properties must define a numeric versionCode")
val appVersionName = versionProperties.getProperty("versionName")?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("version.properties must define a non-empty versionName")
require(appVersionCode > 0) {
    "version.properties versionCode must be greater than 0"
}

android {
    namespace = "com.relaychat.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.relaychat.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["appLabel"] =
            if (project.hasProperty("normalBuild")) "@string/app_name" else "RelayChat Test"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            val normalBuild = project.hasProperty("normalBuild")
            applicationIdSuffix = if (normalBuild) null else ".debug"
            versionNameSuffix = if (normalBuild) null else "-test"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython(selectedBuildPython)
        pip {
            install("numpy")
            install("matplotlib")
        }
    }
}
