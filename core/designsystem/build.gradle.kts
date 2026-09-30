plugins {
    alias(libs.plugins.taski.android.library)
    alias(libs.plugins.taski.android.compose)
}

android {
    namespace = "io.github.alinourix.taski.core.designsystem"
}

dependencies {
    api(libs.compose.material3)
    // Material Color Utilities (HCT, schemes, harmonisation) ship inside MDC.
    implementation(libs.google.material)
    implementation(libs.androidx.core.ktx)
}
