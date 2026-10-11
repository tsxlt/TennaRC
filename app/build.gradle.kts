import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

val uploadKey = (System.getenv("TENNA_UPLOAD_KEY") ?: "${System.getProperty("user.home")}/secrets/tenna/upload.properties")
    .let(::File).takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "lt.tsx.tennarc"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "lt.tsx.tennarc"
        minSdk = 26
        targetSdk = 36
        val version = providers.appVersionFromGit(base = "0.1.0", dir = rootDir)
        versionCode = version.code
        versionName = version.name
        buildConfigField("String", "SITE", "\"https://tenna.tsx.lt\"")
        buildConfigField("String", "REPORT_KEY", "\"BAT7rWiwiz8e0DYQhQZwyD2wZ2gdEqr7zw0oLV7KmsfY5kJbf6ZgnfIgpKgYp10COAtaJHJI+xKAjvr4g0K13JY=\"")
        buildConfigField("String", "CRASH_REPORT_EMAIL", "\"info@neko.lt\"")
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = File(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                )
                it.maxHeapSize = "4g"
            }
        }
    }
}

dependencies {
    implementation(libs.dadb)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.window)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.glance.appwidget.testing)
}
