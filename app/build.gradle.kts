plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ai.jarvis"
    compileSdk = 36
    defaultConfig { applicationId = "ai.jarvis"; minSdk = 28; targetSdk = 36; versionCode = 8; versionName = "0.5.3"; testInstrumentationRunner = "ai.jarvis.SmokeInstrumentation" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        getByName("debug") {
            System.getenv("JARVIS_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { implementation("net.java.dev.jna:jna:5.18.1@aar"); implementation("com.alphacephei:vosk-android:0.3.75@aar"); testImplementation("junit:junit:4.13.2") }
