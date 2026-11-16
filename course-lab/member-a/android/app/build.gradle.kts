plugins { id("com.android.application") }
android {
    namespace = "com.example.wardrobemanager"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.example.wardrobemanager"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.example.wardrobemanager.view.AccountSmokeRunner"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
