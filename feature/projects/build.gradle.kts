plugins {
    alias(libs.plugins.taski.android.feature)
}

android {
    namespace = "io.github.alinourix.taski.feature.projects"
}

dependencies {
    implementation(libs.reorderable)
}
