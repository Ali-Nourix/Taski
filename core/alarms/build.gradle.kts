plugins {
    alias(libs.plugins.taski.android.library)
    alias(libs.plugins.taski.android.compose)
    alias(libs.plugins.taski.hilt)
}

android {
    namespace = "io.github.alinourix.taski.core.alarms"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
