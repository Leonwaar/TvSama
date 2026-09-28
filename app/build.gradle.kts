plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val updateRemote = providers.exec { commandLine("git", "config", "--get", "remote.origin.url") }.standardOutput.asText.get().trim()
val updateRepository = Regex("github\\.com[:/]([^/]+/[^/]+?)(?:\\.git)?$").find(updateRemote)?.groupValues?.get(1).orEmpty()
val tmdbReadToken = providers.gradleProperty("tmdbReadToken")
    .orElse(providers.environmentVariable("TMDB_READ_TOKEN"))
    .orElse("")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "fr.nekotv"
    compileSdk = 36

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    defaultConfig {
        applicationId = "fr.nekotv"
        minSdk = 23
        targetSdk = 36
        versionCode = 73
        versionName = "v1.1.72"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
        buildConfigField("String", "TMDB_READ_TOKEN", "\"$tmdbReadToken\"")
    }

    splits { abi { isEnable = false }; density { isEnable = false } }

    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":streamflix"))
    implementation("com.github.bumptech.glide:glide:5.0.0-rc01")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    implementation("com.google.zxing:core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.withType<Test>().configureEach {
    systemProperty("tvsama.networkAudit", providers.gradleProperty("networkAudit").orElse("false").get())
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
