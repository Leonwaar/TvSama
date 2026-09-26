pluginManagement {
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

rootProject.name = "NekoTV"
include(":app")
include(":streamflix")
include(":streamflix:navigation")
include(":streamflix:retrofit-jsoup-converter")

project(":streamflix").projectDir = file("streamflix/app")
project(":streamflix:navigation").projectDir = file("streamflix/navigation")
project(":streamflix:retrofit-jsoup-converter").projectDir = file("streamflix/retrofit-jsoup-converter")
