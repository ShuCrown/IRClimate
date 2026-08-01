plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// ── 版本号自动递增 ──────────────────────────────────────────
val versionFile = rootProject.file("version.properties")
val versionProps = java.util.Properties()

if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}

var currentVersionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt()
var currentVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"

// 每次 assemble 前递增版本号
val incrementVersionTask = tasks.register("incrementVersion") {
    doLast {
        val props = java.util.Properties()
        versionFile.inputStream().use { props.load(it) }

        val vc = (props.getProperty("VERSION_CODE") ?: "1").toInt() + 1
        val vn = props.getProperty("VERSION_NAME") ?: "1.0.0"
        val parts = vn.split(".").toMutableList()
        if (parts.size == 3) {
            parts[2] = (parts[2].toInt() + 1).toString()
        } else {
            parts.add("1")
        }
        val newVn = parts.joinToString(".")

        props.setProperty("VERSION_CODE", vc.toString())
        props.setProperty("VERSION_NAME", newVn)
        versionFile.outputStream().use { props.store(it, "Auto-incremented by build") }

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
