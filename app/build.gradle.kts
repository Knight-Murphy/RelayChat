plugins {
    id("com.android.application")
}

val versionProperties = java.util.Properties().apply {
    val versionFile = rootProject.file("version.properties")
    require(versionFile.isFile) {
        "Missing version.properties at ${versionFile.absolutePath}"
    }
    versionFile.inputStream().use(::load)
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
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
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
}
