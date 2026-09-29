plugins {
    alias(libs.plugins.taski.android.application)
    alias(libs.plugins.taski.android.compose)
    alias(libs.plugins.taski.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.alinourix.taski"

    defaultConfig {
        applicationId = "io.github.alinourix.taski"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Only the two languages the app is written in.
        localeFilters += listOf("en", "fa")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so a release build installs for testing; replace for publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions.unitTests.all {
        it.maxHeapSize = "3g"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:alarms"))
    implementation(project(":feature:today"))
    implementation(project(":feature:board"))
    implementation(project(":feature:projects"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:timer"))
    implementation(project(":feature:tags"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.google.material)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}

roborazzi {
    // Screenshots of the real app, rendered on the JVM, kept with the docs.
    outputDir.set(rootProject.file("docs/screenshots"))
}
