import java.util.Properties

plugins {
    alias(libs.plugins.taski.android.application)
    alias(libs.plugins.taski.android.compose)
    alias(libs.plugins.taski.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val appVersion = "0.1.0"

/**
 * Release signing, in order of preference: environment variables (CI), a git-ignored
 * keystore.properties in the project root, and otherwise the public example key in
 * signing/, so a signed release builds anywhere without a key of your own.
 */
val ownKey = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(env: String, property: String, example: String): String =
    providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
        ?: ownKey.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: example

android {
    namespace = "io.github.alinourix.taski"

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingValue("TASKI_KEYSTORE", "storeFile", "signing/example-release.jks"))
            storePassword = signingValue("TASKI_KEYSTORE_PASSWORD", "storePassword", "taski-example")
            keyAlias = signingValue("TASKI_KEY_ALIAS", "keyAlias", "taski")
            keyPassword = signingValue("TASKI_KEY_PASSWORD", "keyPassword", "taski-example")
        }
    }

    defaultConfig {
        applicationId = "io.github.alinourix.taski"
        versionCode = 1
        versionName = appVersion
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    testOptions.unitTests.all {
        it.maxHeapSize = "2g"
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

/** The signed release APK and install notes, zipped: app/build/dist/taski-<version>-release.zip. */
tasks.register<Zip>("zipRelease") {
    group = "distribution"
    description = "Builds the signed release APK and zips it with the install notes."
    dependsOn("assembleRelease")
    // A local copy, so the rename lambda captures a string rather than the build script.
    val apkName = "taski-$appVersion-release.apk"
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        rename { apkName }
    }
    from(rootProject.file("docs/INSTALL.md"))
    archiveFileName.set("taski-$appVersion-release.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

roborazzi {
    // Screenshots of the real app, rendered on the JVM, kept with the docs.
    outputDir.set(rootProject.file("docs/screenshots"))
}
