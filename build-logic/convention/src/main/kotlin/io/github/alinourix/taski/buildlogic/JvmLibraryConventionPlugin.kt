package io.github.alinourix.taski.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.api.JavaVersion

/** Plain Kotlin/JVM module: no Android framework, so its logic is testable on the JVM alone. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
        dependencies {
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlin-test"))
        }
    }
}
