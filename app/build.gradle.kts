plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "it.gestioneclienti"; compileSdk = 35
 defaultConfig { applicationId = "it.gestioneclienti"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.8.0" } 

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}
}

 dependencies { implementation("org.apache.poi:poi-ooxml:5.2.5") }
