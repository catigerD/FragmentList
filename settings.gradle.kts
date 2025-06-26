pluginManagement {
    repositories {
        maven {
            setUrl("https://maven.aliyun.com/repository/google")
        }

        maven {
            setUrl("https://maven.aliyun.com/repository/public")
        }

        maven {
            setUrl("https://maven.aliyun.com/repository/jcenter")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            setUrl("https://maven.aliyun.com/repository/google")
        }

        maven {
            setUrl("https://maven.aliyun.com/repository/public")
        }

        maven {
            setUrl("https://maven.aliyun.com/repository/jcenter")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "FragmentList"
include(":app")
