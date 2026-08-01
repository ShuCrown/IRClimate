pluginManagement {
    val isGitHubActions = System.getenv("GITHUB_ACTIONS")?.equals("true", ignoreCase = true) == true
    repositories {
        if (isGitHubActions) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
    }
}
dependencyResolutionManagement {
    val isGitHubActions = System.getenv("GITHUB_ACTIONS")?.equals("true", ignoreCase = true) == true
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isGitHubActions) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
    }
}
rootProject.name = "IrPoc"
include(":app")
