plugins {
    alias(libs.plugins.taski.android.library)
    alias(libs.plugins.taski.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.alinourix.taski.core.data"
    testOptions.unitTests.all {
        // The schema-parity test compares Room's exported schema with the draft Supabase migration.
        it.systemProperty("taski.rootDir", rootDir.absolutePath)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
