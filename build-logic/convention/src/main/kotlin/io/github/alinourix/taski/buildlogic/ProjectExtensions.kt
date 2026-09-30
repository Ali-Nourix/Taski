package io.github.alinourix.taski.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal object TaskiSdk {
    const val COMPILE = 37
    /** Compose 1.13 needs the 37.1 platform. */
    const val COMPILE_MINOR = 1
    const val TARGET = 36
    const val MIN = 26
}

/** Shared Android settings. AGP 9 compiles Kotlin itself, so no Kotlin plugin is applied here. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = TaskiSdk.COMPILE
        compileSdkMinor = TaskiSdk.COMPILE_MINOR
        defaultConfig.minSdk = TaskiSdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-Xannotation-default-target=param-property",
            )
        }
    }
}
