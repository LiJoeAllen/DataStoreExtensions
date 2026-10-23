pluginManagement {
    repositories {
        // 国内镜像（阿里云），官方源作兜底
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 本地 Maven（开发时优先消费 publishToMavenLocal 的产物）
//        mavenLocal()
        // GitHub Packages 正式发布产物；需认证（PAT 勾选 read:packages）：
        //   GITHUB_ACTOR=LiJoeAllen  GITHUB_TOKEN=<PAT>
        // 未设置时匿名访问被 401 跳过，回落到上面的 mavenLocal
        maven("https://maven.pkg.github.com/LiJoeAllen/DataStoreExtensions") {
            name = "GitHubPackages"
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        // 国内镜像（阿里云），官方源作兜底
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "DataStoreExtensions"

include(":app")
include(":datastore-extensions")
include(":persist-plugin")
