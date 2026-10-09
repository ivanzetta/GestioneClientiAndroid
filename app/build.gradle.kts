plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "it.gestioneclienti"; compileSdk = 35
 defaultConfig { applicationId = "it.gestioneclienti"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.5" } }

 dependencies { implementation("org.apache.poi:poi-ooxml:5.2.5") }
