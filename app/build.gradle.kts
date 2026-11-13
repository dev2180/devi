plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devi.overlay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devi.overlay"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Her phone is arm64; the bundled Python/yt-dlp is large, so only this ABI ships.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // youtubedl-android runs its Python from extracted native libraries.
    packaging { jniLibs { useLegacyPackaging = true } }

    // Her chant videos ship inside the app and play straight from it, so they must stay uncompressed.
    androidResources { noCompress += listOf("webm", "mp4", "m4a") }

    buildTypes {
        // The one she runs: not debuggable, shrunk by R8, and signed with the same key as before,
        // so it installs over the old app and keeps every picture and setting.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
}
