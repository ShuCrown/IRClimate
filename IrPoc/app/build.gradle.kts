plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// ── 版本号读取（来源: version.properties，由 CI 工作流管理递增）──
val versionFile = rootProject.file("version.properties")

fun readVersionCode(): Int {
    if (!versionFile.exists()) return 1
    return versionFile.readLines()
        .firstOrNull { it.startsWith("VERSION_CODE=") }
        ?.substringAfter("=")
        ?.trim()
        ?.toIntOrNull() ?: 1
}

fun readVersionName(): String {
    if (!versionFile.exists()) return "1.0.0"
    return versionFile.readLines()
        .firstOrNull { it.startsWith("VERSION_NAME=") }
        ?.substringAfter("=")
        ?.trim()
        ?.ifEmpty { null } ?: "1.0.0"
}

val appVersionCode = readVersionCode()
val appVersionName = readVersionName()

println("📦 Current version: $appVersionName (code $appVersionCode)")

android {
    namespace = "com.example.irpoc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.irpoc"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    debugImplementation(libs.ui.tooling)
}