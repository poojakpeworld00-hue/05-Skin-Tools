plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fffemotes.skintools.ads"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.androidx.browser)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.shimmer)
    implementation(libs.gson)
    implementation(libs.glide)
    // Ad layouts use @dimen/_Nsdp / _Nssp.
    implementation(libs.sdp)
    implementation(libs.ssp)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
