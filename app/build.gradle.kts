import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val releaseSigningFile = providers.environmentVariable("PICAL_SIGNING_PROPERTIES")
    .map { rootProject.file(it) }.getOrElse(rootProject.file(".signing/keystore.properties"))
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val releaseSigningConfigured = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigningProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.ahmedismail.flowtrack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ahmedismail.flowtrack"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (releaseSigningConfigured) create("release") {
            storeFile = releaseSigningFile.parentFile.resolve(releaseSigningProperties.getProperty("storeFile"))
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
            storeType = releaseSigningProperties.getProperty("storeType", "PKCS12")
        }
    }
    buildTypes {
        release {
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    // Opt-in, device-only PDF QA entry point; ordinary builds exclude the harness.
    if (providers.gradleProperty("flowtrackReportPreview").orNull == "true") {
        sourceSets.getByName("debug").java.srcDirs("../tools/report-preview", "../tools/report-fixtures")
    }
    // Both languages must remain available for the in-app language switch.
    bundle { language { enableSplit = false } }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

val checkReleaseSigning by tasks.registering {
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is required. Configure .signing/keystore.properties or PICAL_SIGNING_PROPERTIES; see docs/RELEASE.md."
        }
        check(releaseSigningFile.parentFile.resolve(releaseSigningProperties.getProperty("storeFile")).isFile) {
            "Configured release keystore does not exist."
        }
    }
}
tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease", "signReleaseBundle")) dependsOn(checkReleaseSigning)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (offline-first local persistence)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Reorderable list for drag-to-reorder Workflow settings
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // Image loading for photo-attachment thumbnails (camera/gallery URIs)
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
