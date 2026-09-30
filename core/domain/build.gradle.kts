plugins {
    alias(libs.plugins.taski.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation("javax.inject:javax.inject:1")
    testImplementation(libs.kotlinx.coroutines.test)
}
