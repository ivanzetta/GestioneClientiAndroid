plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "it.gestioneclienti"; compileSdk = 35
<<<<<<< HEAD
 defaultConfig { applicationId = "it.gestioneclienti"; minSdk = 26; targetSdk = 35; versionCode = 8; versionName = "0.7.1" } 
=======
 defaultConfig { applicationId = "it.gestioneclienti"; minSdk = 26; targetSdk = 35; versionCode = 7; versionName = "0.7" } 
>>>>>>> e8b79cb287858e35760cb4fcfc44242ed3b82933

compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}
}

 dependencies { implementation("org.apache.poi:poi-ooxml:5.2.5") }
