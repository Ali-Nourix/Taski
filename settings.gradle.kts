pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Taski"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:alarms")
include(":feature:today")
include(":feature:board")
include(":feature:projects")
include(":feature:editor")
include(":feature:timer")
include(":feature:tags")
include(":feature:settings")
