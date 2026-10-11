plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Android only installs an APK over the existing app when (1) it is signed with the same key and
// (2) its versionCode is higher. GitHub Actions numbers every run (1, 2, 3, ...), which gives (2) for free.
// Builds on your own computer have no run number and fall back to 1.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "app.scrapbook"
    compileSdk = 35
    defaultConfig { applicationId = "app.scrapbook"; minSdk = 26; targetSdk = 35; versionCode = runNumber; versionName = "0.1.$runNumber" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
    // Your own signing key (see SIGNING.md). The workflow provides it through these environment variables.
    // Without it, Gradle falls back to a throwaway debug key that changes on every CI run, and Android
    // then refuses to update the installed app ("package conflicts with an existing package").
    val keystoreFile = System.getenv("KEYSTORE_FILE")
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreFile); storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
        buildTypes {
            // Debug and release use the same key, so either kind of APK updates the other.
            getByName("debug") { signingConfig = signingConfigs.getByName("release") }
            getByName("release") { signingConfig = signingConfigs.getByName("release") }
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
