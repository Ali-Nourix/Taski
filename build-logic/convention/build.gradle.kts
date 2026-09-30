plugins {
    `kotlin-dsl`
}

group = "io.github.alinourix.taski.buildlogic"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "taski.android.application"
            implementationClass = "io.github.alinourix.taski.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "taski.android.library"
            implementationClass = "io.github.alinourix.taski.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "taski.android.compose"
            implementationClass = "io.github.alinourix.taski.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "taski.android.feature"
            implementationClass = "io.github.alinourix.taski.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "taski.hilt"
            implementationClass = "io.github.alinourix.taski.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "taski.jvm.library"
            implementationClass = "io.github.alinourix.taski.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
