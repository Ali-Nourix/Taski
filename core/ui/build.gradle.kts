plugins {
    alias(libs.plugins.taski.android.library)
    alias(libs.plugins.taski.android.compose)
}

android {
    namespace = "io.github.alinourix.taski.core.ui"
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:designsystem"))
    implementation(libs.androidx.core.ktx)
}
