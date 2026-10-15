plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ai.jarvis"
    compileSdk = 36
    defaultConfig { applicationId = "ai.jarvis"; minSdk = 28; targetSdk = 36; versionCode = 2; versionName = "0.2.0"; testInstrumentationRunner = "ai.jarvis.SmokeInstrumentation" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { implementation("net.java.dev.jna:jna:5.18.1@aar"); implementation("com.alphacephei:vosk-android:0.3.75@aar"); testImplementation("junit:junit:4.13.2") }
