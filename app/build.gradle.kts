import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.campusconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.campusconnect"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${localProperties.getProperty("GEMINI_MODEL", "gemini-2.5-flash")}\"")
        // HMAC secret for QR ticket signing. Overridable per-deployment via
        // local.properties (never committed). The app has no backend, so the
        // same secret must exist in every install for cross-device ticket
        // verification; the default is a fallback so the app still builds
        // without local configuration. CryptoUtils reads this via reflection.
        buildConfigField(
            "String",
            "TICKET_HMAC_SECRET",
            "\"${localProperties.getProperty("TICKET_HMAC_SECRET", "CampusConnect_Secure_HMAC_Secret_Key_2026")}\""
        )
        // Organizer self-signup requires this invite code. Blank = organizer
        // self-signup disabled (admins promote accounts instead).
        buildConfigField("String", "ORGANIZER_INVITE_CODE", "\"${localProperties.getProperty("ORGANIZER_INVITE_CODE", "")}\"")
        // Password for the seeded admin account in RELEASE builds. Blank = no
        // admin is seeded. Demo accounts are only ever created in debug builds.
        buildConfigField("String", "ADMIN_INITIAL_PASSWORD", "\"${localProperties.getProperty("ADMIN_INITIAL_PASSWORD", "")}\"")
    }

    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Refuse to ship a release build with the public default ticket secret or debug signing.
gradle.taskGraph.whenReady {
    val releasing = allTasks.any { it.project == project && (it.name == "assembleRelease" || it.name == "bundleRelease") }
    if (releasing && localProperties.getProperty("TICKET_HMAC_SECRET").isNullOrBlank()) {
        throw GradleException("Set TICKET_HMAC_SECRET in local.properties before building a release.")
    }
    if (releasing && localProperties.getProperty("RELEASE_STORE_FILE") == null) {
        throw GradleException("Set RELEASE_STORE_FILE (and related keys) in local.properties before building a release.")
    }
}

// Export Room schemas to a versioned directory so migrations can be tested and
// reviewed. Requires exportSchema = true on the @Database.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.zxing.android.embedded)

    implementation(libs.coil)
    implementation(libs.coil.svg)

    implementation(libs.androidx.work.runtime.ktx)
    // Pinned so Room 2.8's migration test helper (androidTest) finds a compatible serialization runtime.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")


    implementation(libs.materialCalendarView)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
}