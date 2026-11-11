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
        maven { url = uri("https://artifactory.appodeal.com/appodeal") }
        maven { url = uri("https://artifactory.bidon.org/bidon") }
    }
}
rootProject.name = "ShiftCyber"
include(":app")
