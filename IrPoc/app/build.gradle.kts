plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// ── 版本号自动递增（配置阶段执行）────────────────────────────
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

fun writeVersion(code: Int, name: String) {
    versionFile.writeText("VERSION_CODE=$code\nVERSION_NAME=$name\n")
}

fun incrementVersionName(vn: String): String {
    val parts = vn.split(".").toMutableList()
    if (parts.size == 3) {
        parts[2] = (parts[2].toInt() + 1).toString()
    } else {
        parts.add("1")
    }
    return parts.joinToString(".")
}

// 在配置阶段直接读取旧值、递增、写回，确保 defaultConfig 拿到最新值
val oldVersionCode = readVersionCode()
val oldVersionName = readVersionName()
val newVersionCode = oldVersionCode + 1
val newVersionName = incrementVersionName(oldVersionName)
writeVersion(newVersionCode, newVersionName)

println("📦 Version bumped: $oldVersionName → $newVersionName (code $newVersionCode)")

android {
    namespace = "com.example.irpoc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.irpoc"
        minSdk = 24
        targetSdk = 34
        versionCode = newVersionCode
        versionName = newVersionName
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

    signingConfigs {
        debug {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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