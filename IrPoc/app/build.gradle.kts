plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// ── 版本号自动递增 ──────────────────────────────────────────
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

var currentVersionCode = readVersionCode()
var currentVersionName = readVersionName()

val incrementVersionTask = tasks.register("incrementVersion") {
    doLast {
        val vc = readVersionCode() + 1
        val vn = readVersionName()
        val newVn = incrementVersionName(vn)

        writeVersion(vc, newVn)

        // 更新当前构建的值
        currentVersionCode = vc
        currentVersionName = newVn

        println("📦 Version bumped: $vn → $newVn (code $vc)")
    }
}

// 在 preBuild 之前执行版本递增，确保 versionCode/versionName 是最新的
tasks.named("preBuild") {
    dependsOn(incrementVersionTask)
}

android {
    namespace = "com.example.irpoc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.irpoc"
        minSdk = 24
        targetSdk = 34
        versionCode = currentVersionCode
        versionName = currentVersionName
    }

    buildTypes {
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