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
        // JitPack：app 消费正式发布产物（匿名，无需凭据）
        maven("https://jitpack.io")
        // 本地开发如需覆盖远程产物，解开下一行并 publishToMavenLocal 同坐标
        // mavenLocal()
        // GitHub Packages 备用渠道；需认证（PAT 勾选 read:packages）：
        //   GITHUB_ACTOR=LiJoeAllen  GITHUB_TOKEN=<PAT>
        // 未设置时匿名访问被 401 跳过
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
