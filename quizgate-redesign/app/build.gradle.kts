plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.quizgate.app"
    compileSdk = 35
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig {
        applicationId = "com.quizgate.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-dev"
    }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
