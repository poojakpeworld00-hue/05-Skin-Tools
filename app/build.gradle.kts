plugins {
    alias(libs.plugins.android.application)
}
// Firebase (Remote Config, Analytics, Crashlytics, FCM) needs app/google-services.json from the FFF Firebase project.
// Until it is added the app builds and runs on in-code config defaults, with Firebase calls skipped.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// AdMob app id: Google's test id for now, in debug and release. For the real one later, set `admob.appId=…` in
// local.properties (or -PadmobAppId=…); a release build without it warns but still builds.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobAppId: String? = providers.gradleProperty("admobAppId").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
        ?.firstOrNull { it.startsWith("admob.appId=") }?.substringAfter("=")?.trim()?.ifBlank { null }
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    // A local copy: the action must not capture the build script itself (configuration cache can't store it).
    val realId = admobAppId?.takeUnless { it.startsWith("ca-app-pub-3940256099942544") }
    doFirst { if (realId == null) this.logger.warn("AdMob: release uses Google's test app id (no admob.appId in local.properties).") }
}

// Fossify Commons (launcher) pulls the old support library; AndroidX replaces it.
configurations.configureEach { exclude(group = "com.android.support") }

android {
    namespace = "com.fffemotes.skintools.master"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fffemotes.skintools.master"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = admobTestAppId
        }
        release {
            manifestPlaceholders["admobAppId"] = admobAppId ?: admobTestAppId
            proguardFiles("proguard-rules.pro")
            // Full R8 over the whole app (code shrink, obfuscation, optimisation, unused resources), as the reference.
            optimization { enable = true }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":ads"))
    implementation(project(":launcher"))
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.installreferrer)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.sdp)
    implementation(libs.ssp)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.play.app.update)
    implementation(libs.play.review)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}